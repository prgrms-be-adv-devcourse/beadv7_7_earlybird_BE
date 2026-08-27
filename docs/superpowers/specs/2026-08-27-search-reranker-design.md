# 프로젝트 검색: Cross-encoder 리랭커 도입 설계

- 작성일: 2026-08-27
- 대상 서비스: `project-service`
- 상태: 설계 승인됨, 구현 대기
- 선행 조건: **PR #740 (`강대혁/project/search-relevance-improvement`) 머지.** 이 설계는 #740이 도입한 Score-aware fusion, 5필드 벡터, `fetchDocumentsByIds`, `QueryProductCompatibilityEvaluator` 위에 세운다. #740 머지 후 이 브랜치를 develop에 리베이스한다.
- 타이밍: 데모 후 정식 리팩터. 긴급성 없음.

## 1. 배경과 문제

`project-service` 검색은 BM25(nori) + 5필드 kNN(title/summary/description/category/reward)을 Score-aware Hybrid Fusion으로 결합하고, LLM(gpt-4o-mini) 기반 QueryIntent 추출 + 손코딩 Compatibility 평가(`polarOpposites`)로 계절 충돌 상품을 제거한다.

관찰된 실패:

- **"강아지 전용 음식" 검색에 빔프로젝터 12개가 9~20위로 노출.** 빔프로젝터는 BM25 0점, `polarOpposites` 매칭 없음(충돌이 아니라 무관). `text-embedding-3-small`이 "강아지 사료"와 "빔프로젝터"를 코사인 ~0.4로 보고, 5개 벡터가 전부 "카테고리 + 제목 + 요약" 수프라 이 바닥 유사도가 5필드에 동시 누수되어 가중합 ~0.17 → 동적 컷오프(0.168) 통과.
- **저알러지 동결건조 간식(반려동물 카테고리)이 약한 매칭으로 낮게 랭크.** 데이터에 "강아지" 명시가 없어 "간식"~"사료" 의미 유사로만 잡힘.
- 인덱스에 **카테고리명 텍스트 필드가 없다** (`categoryId` 숫자만). BM25는 카테고리를 아예 못 본다.

근본 원인: **관련도를 미리 계산된 임베딩의 선형 가중합으로 판단한다.** 이는 *검색*(후보 빠르게 뽑기) 기법이지 *관련도 판단*("이 문서가 이 질문에 답하나?") 기법이 아니다. 선형합은 "이건 반려동물이고 저건 전자기기다" 같은 쿼리-문서 관계 판단을 표현할 수 없다. `QueryProductCompatibilityEvaluator`는 이 관계 판단을 손코딩 문자열 매칭으로 때운 것이라, 누군가 예상한 케이스(여름/겨울)만 되고 나머지(강아지/빔프로젝터)는 뚫린다.

## 2. 해결 방향

BM25/kNN fusion은 **후보 생성(candidate generation)** 으로 격하하고, (쿼리, 문서)를 함께 읽어 관련도를 직접 점수화하는 **Cross-encoder 리랭커 단계**를 추가한다. 리랭커는 어휘 간극·카테고리 불일치·계절 충돌·카테고리 내부 뉘앙스를 한 단계에서 처리한다.

### 모델 선택: Cohere Rerank 3.5 (호스팅 API)

| 후보 | 탈락 사유 |
|---|---|
| 로컬 cross-encoder (BGE-reranker-v2-m3, TEI 컨테이너) | 배포 여유 메모리 없음. TEI + m3 ~4GB, base도 ~1.5~2GB. |
| LLM 리랭커 (gpt-4o-mini) | 지연 ~1.5s(cross-encoder ~200ms), 비결정적, 출력 파싱 취약, 부하 시 TPM 한계로 tail latency 급증. 유일한 장점("새 벤더 0")은 팀이 새 키 발급에 부담 없다고 확인. |

Cohere Rerank 3.5: 100+ 언어 멀티링구얼, ~200ms, 결정적, 응답이 `{index, relevance_score}` 배열이라 파싱 불필요. Trial API Key는 개발·수동 테스트 용도로 사용하며 rate limit이 존재한다. Production 사용 시 실제 비용과 rate limit은 배포 시점의 Cohere 정책을 따른다.

대안 Jina Reranker v2 — 개발 중 무료 한도 여유가 더 크고 가중치가 공개되어 나중에 자체 호스팅 가능(메모리 확보 시). 구현은 어댑터 인터페이스만 맞추면 벤더 교체 가능하게 한다.

## 3. 설계 원칙: 쿼리 이원화

```
사용자 입력: "강아지 옷"
        │
        ├─→ [확장] "강아지 옷 반려견 의류 애견 옷"
        │         ├─→ BM25       (recall)
        │         └─→ Embedding  (recall)
        │
        └─→ [원본 그대로] "강아지 옷"
                  └─→ Reranker   (사용자 실제 의도로 최종 판단)
```

- **retrieval(BM25/kNN)**: 확장 쿼리. 어휘 간극을 메워 정답을 후보에 넣는다.
- **rerank**: 원본 쿼리(trim만). 확장어가 만든 잘못된 매칭("의류"가 롱코트를 끌어옴)의 노이즈 없이, 사용자가 친 그대로로 판단한다.
- "원본"의 정의: trim한 사용자 입력. 슬랭 정규화(댕댕이→강아지)를 리랭커에도 적용할지는 엣지케이스 — Cohere 멀티링구얼이 슬랭을 알아들을 가능성이 높아 일단 원본 그대로, 골든셋 측정 후 조정.

## 4. 파이프라인

```
doSearch(keyword):
  trimmed = keyword.trim()
  expanded = QuerySynonymExpander.expand(trimmed)          # 인메모리 정적 맵, <1ms

  BM25(expanded) ─┬──────────────────────────
  embed(expanded) → 5×kNN ──────────────────┘
    → fuseByScore(...)                                     # 후보 스코어링. 가중치 튜닝 안 함.
    → 상위 40 후보 projectIds

  SeasonalConflictFilter.filter(trimmed, candidates)       # 강한 충돌만 제외 (안전망)

  docs = fetchDocumentsByIds(candidates)                   # ES terms 쿼리 1회 (기존 메서드 재사용)
  reranked = RerankPort.rerank(trimmed, candidates, docs)  # Cohere 1회 호출
    → 실패/타임아웃 → CircuitBreaker fallback: candidates 순서 그대로

  return reranked
```

### 제거

- `QueryIntentAnalyzer` (LLM 호출), `QueryIntent`, `Requirement`
- `QueryProductCompatibilityEvaluator` → `SeasonalConflictFilter`로 축소 대체
- config `spring.ai.openai.chat.*` (project-service에서 chat 모델 안 씀)
- 관련 테스트: `QueryIntentAnalyzerTest`, `QueryProductCompatibilityEvaluatorTest`, `CompatibilityQualityDeepEvaluationTest`, `QueryIntentSearchQualityTest`

### 유지 (재색인 불필요)

- `ProjectEmbeddingService` / 색인 시점 5필드 벡터 생성 — 그대로
- `ProjectDocument`, ES 매핑 — 그대로
- `fuseByScore` 수학 — 그대로. 역할만 "후보 생성"으로. `categoryIntentBoost`도 유지(싸고 recall에 도움).
- 검색 시점 임베딩 대상만 `enrichedQuery` → `expanded`로 (색인 무관)

## 5. 컴포넌트

| 컴포넌트 | 위치 | 책임 | 의존 |
|---|---|---|---|
| `RerankPort` | `application/port/` | 인터페이스. `List<Long> rerank(String originalQuery, List<Long> candidateIds, Map<Long, ProjectDocument> docs)` | 없음 |
| `CohereRerankAdapter` | `infrastructure/search/` | `RerankPort` 구현. 후보 문서 텍스트(title + summary) 모아 요청 조립 → `CohereRerankClient` 호출 → 응답 index를 projectId 순서로 매핑. `CircuitBreakerFactory`로 감싸고 fallback은 `candidateIds` 그대로 반환. | `CohereRerankClient`, `CircuitBreakerFactory` |
| `CohereRerankClient` | `infrastructure/search/` | Spring `RestClient` 래퍼. `POST {base-url}/v2/rerank`. Cohere는 Eureka 밖 외부 URL이라 `@FeignClient` 대신 `RestClient`(Spring 기본). | `RestClient`, config |
| `NoOpRerankAdapter` | `infrastructure/search/` | `RerankPort` 구현. `candidateIds` 그대로 반환. `cohere.rerank.enabled=false`일 때 활성. 부하 테스트·Cohere 장애 대비. | 없음 |
| `QuerySynonymExpander` | `infrastructure/search/` | 원본 → 확장 쿼리 문자열. 기존 `ProjectSearchAdapter.SLANG_SYNONYM_MAP` 개념 확장, 인메모리 정적 맵. BM25/임베딩 전용. | 없음 |
| `SeasonalConflictFilter` | `infrastructure/search/` | 강한 계절 충돌만 하드 제외. 쿼리가 계절 X를 명확히 함의 **AND** 문서 title/summary에 반대 계절 강한 마커 명시일 때만 후보에서 제거. 하드코딩 계절→마커 맵 ~25줄. | 없음 |

### `RerankPort` 설계 근거

- 포트가 `Map<Long, ProjectDocument> docs`를 받는다: 문서 텍스트 조회는 `ProjectSearchAdapter`가 이미 하는 `fetchDocumentsByIds` 한 번으로 끝내고, 어댑터는 그 결과만 쓴다. 어댑터가 다시 조회하면 책임 중복 + N+1 위험.
- 반환은 `List<Long>` (재정렬된 projectId). 점수는 파이프라인 하류에서 안 쓴다(최종 컷은 `ProjectServiceImpl`이 MySQL 가시성 필터링 후 수행).

### Bean 조건부 생성 (`cohere.rerank.enabled`)

- `cohere.rerank.enabled=true`: `CohereRerankClient` + `CohereRerankAdapter` Bean 생성. `COHERE_API_KEY` 필요.
- `cohere.rerank.enabled=false` (또는 미설정): `CohereRerankClient`/`CohereRerankAdapter` **Bean 생성 안 함** → `COHERE_API_KEY` **불필요**. `NoOpRerankAdapter`만 활성.
- 구현: Cohere Bean들에 `@ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")`, `NoOpRerankAdapter`에 `@ConditionalOnMissingBean(RerankPort.class)` (또는 `havingValue = "false", matchIfMissing = true`).
- CI는 `cohere.rerank.enabled=false`로 돌아 키 없이 컨텍스트가 뜬다.

## 6. Cohere 요청/응답

```
POST https://api.cohere.com/v2/rerank
Authorization: Bearer ${COHERE_API_KEY}
{
  "model": "rerank-v3.5",
  "query": "강아지 옷",
  "documents": ["저알러지 동결건조 간식 ...", "강아지용 산책줄 ...", ...],   // 40개, title + " " + summary
  "top_n": 40
}

→ 200
{ "results": [ {"index": 12, "relevance_score": 0.94}, {"index": 3, "relevance_score": 0.71}, ... ] }
```

- `documents[i]` ↔ `candidateIds.get(i)` 매핑을 어댑터가 보관. 응답 `results[].index`를 그 매핑으로 projectId로 되돌려 `results` 순서대로 리스트 구성.
- `top_n`은 후보 수(40)와 동일하게 요청 — 전부 재정렬해서 받고, 최종 개수 컷은 하류에 맡긴다.
- 문서 텍스트: `title + " " + summary`. description은 길고 노이즈가 많아 제외(골든셋 측정 후 추가 검토).

## 7. Resilience

| 항목 | 값 | 근거 |
|---|---|---|
| `projectRerank` TimeLimiter | **초기 1.5s** | Cohere 실측 보통 100~400ms. 부하/지연 측정 후 800ms~1s로 조일 가능성. **측정 기반 확정.** |
| CircuitBreaker | 기존 `projectSearch` 계열 설정 재사용 (`slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=10s`) | |
| Fallback | `candidateIds` 그대로 반환 (fusion 스코어순) | 검색은 정상 동작, rerank 품질만 빠짐. 기존 `searchFallback`(DB LIKE)과 같은 graceful degradation 패턴. |
| `projectQueryIntent` TimeLimiter | **제거** | QueryIntentAnalyzer 삭제 |

### 지연 예상 (tier-2, cold)

```
retrieval(BM25 + embed + 5×kNN) ~0.5s
  → SeasonalConflictFilter ~0ms
  → fetchDocumentsByIds (ES terms 1회) ~30ms
  → Cohere rerank ~300ms
≈ 0.8~1s   (현재 gpt-4o-mini QueryIntent 경로 ~2s에서 개선)
```

## 8. N+1 방지 (명시적 제약)

후보 40개에 대해:

- 문서 텍스트: `fetchDocumentsByIds(candidateIds)` — ES `terms` 쿼리 **1회**. title/summary가 `ProjectDocument`(ES)에 있으므로 DB 안 감.
- 최종 엔티티: `ProjectServiceImpl.findAll`의 `projectRepository.findAll(spec)` — `project_id IN (...)` **1회** (기존 동작, 로그 확인됨).
- Cohere 호출 **1회**.

문서/엔티티를 후보 개수만큼 조회하는 코드는 리뷰에서 반드시 리젝.

## 9. Config (config 레포 `project-service.yml`)

```yaml
cohere:
  rerank:
    enabled: true                      # false → CohereRerankClient/Adapter Bean 미생성, 키 불필요, NoOpRerankAdapter
    base-url: https://api.cohere.com
    model: rerank-v3.5
    top-n: 40
    api-key: ${COHERE_API_KEY}          # 기본값 없음 (2026-08-27 키 유출 교훈). enabled=true일 때만 참조됨.

# 제거:
# spring:
#   ai:
#     openai:
#       chat: ...
```

`cohere.rerank.enabled=false`인 환경(CI 등)은 `COHERE_API_KEY` 없이 부팅된다 (§5 Bean 조건부 생성 참조).

`beadv7_7_earlybird_config`는 이 리포에서 직접 수정 불가 — config 레포에 별도 커밋.
`project-service/src/test/resources/application.yml`에도 동일 값 추가(테스트 컨텍스트).

## 10. 테스트

### 단위

| 대상 | 검증 |
|---|---|
| `CohereRerankAdapter` | `CohereRerankClient` 목킹 → 요청 형태(원본 쿼리·`title+summary`·`top_n`=후보수), 응답 `index`→projectId 순서 매핑, 클라이언트 예외 시 fallback = candidateIds 그대로 |
| `CohereRerankClient` | `RestClient` `MockRestServiceServer` → URL/헤더/바디, 4xx·5xx·타임아웃 → 예외 전파 |
| `QuerySynonymExpander` | 확장 맵 케이스, 무매칭 시 원본 그대로 |
| `SeasonalConflictFilter` | 강한 충돌 제외 / 쿼리 계절 불명확 시 무동작 / 문서에 명시 마커 없으면 유지 |
| `ProjectSearchAdapter.doSearch` | `RerankPort` 목킹한 전체 플로우, fallback 경로 |

### 통합 / 골든셋

**외부 Cohere API 호출을 일반 CI 머지 게이트로 만들지 않는다.** CI에서 실 벤더를 두들기면 느리고, 키가 필요하고, 벤더 상태에 CI가 좌우된다.

| 실행 | 리랭커 | 검증 |
|---|---|---|
| **CI (일반 머지 게이트)** | `NoOpRerankAdapter` | retrieval / `QuerySynonymExpander` / `SeasonalConflictFilter` 회귀. rerank가 빠진 상태에서도 후보 생성·필터가 깨지지 않는지. |
| **실 Cohere (로컬/수동)** | `CohereRerankAdapter` | reranking 품질 평가. `@Assumptions.assumeTrue(COHERE_API_KEY 존재)`로 CI에선 skip (기존 `QueryIntentE2ESearchQualityTest` 패턴). **#740 머지 상태의 baseline 대비 precision@5 / nDCG@10 확인.** |

- 리랭커 PR 머지 조건 = ① CI 그린 + ② 로컬 실 Cohere 평가에서 baseline **이상** (수동 확인, 리뷰어에게 수치 제시).
- baseline 캡처: #740 머지 직후 현재 시스템으로 골든셋 실행해 precision@5 / nDCG@10 기록.
- 지표: 라벨링 쿼리 ~30~40개. 골든셋 확장 커버 유형: 정확매칭 / 자연어 / **크로스카테고리 노이즈(강아지음식→빔프로젝터)** / 계절 / 어휘간극. **이 골든셋 구축이 이번 작업의 최대 공수일 수 있다.**

### 부하 테스트

- `cohere.rerank.enabled=false` (NoOp)로 실행 → **우리 인프라만** 측정.
- 별도 스크립트로 Cohere p50/p95 + RPM 상한 측정 → "rerank +Xms, 벤더 한도 N RPM" 문서화.
- 측정된 p99로 `projectRerank` TimeLimiter 확정.

## 11. 롤아웃

- 브랜치 `강대혁/project/search-reranker` 1개, PR 1개 (#740 머지 후 develop에 리베이스).
- 커밋 순서:
  1. `RerankPort` + `CohereRerankAdapter` + `CohereRerankClient` + `NoOpRerankAdapter` + config + 단위 테스트
  2. `QuerySynonymExpander` + `SeasonalConflictFilter` + 단위 테스트
  3. `doSearch` 배선 (동의어 확장, 후보 40, 필터, rerank 호출) + `QueryIntentAnalyzer`/`QueryProductCompatibilityEvaluator` 및 관련 테스트 제거
  4. 골든셋 baseline 캡처 + 비교 + 골든셋 확장
- config 레포 커밋 (Cohere config, chat 모델 제거).
- `project-service/TECHNICAL_DECISIONS_Concurrency_Search_FileService.txt`에 결정 근거 기록 (왜 로컬 아님 / 왜 LLM 리랭커 아님 / 쿼리 이원화 원칙).
- 데모 후라 긴급성 없음. develop 대상, 팀 리드 머지.

## 12. 리스크

| 리스크 | 완화 |
|---|---|
| **Cohere는 최종 검색 품질에 대한 외부 의존성이다.** 장애 시 fusion 순서로 fallback하여 검색 기능 자체는 유지하지만, 순위 품질은 fusion 수준으로 떨어진다(빔프로젝터 노이즈 복귀). | Fallback으로 검색은 살아있음. `cohere.rerank.enabled=false`로 즉시 NoOp 전환. graceful degradation. |
| retrieval이 정답을 top-40에서 놓치면 리랭커가 못 살림 (recall 천장). | 후보 20→40 확대. 골든셋으로 recall@40 측정. 5벡터 유지(수프가 retrieval recall엔 도움). |
| 골든셋이 얇으면 게이트가 약함. | 골든셋 확장을 작업 범위에 명시. baseline 대비 비교로 회귀 방지. |
| Trial 키 rate limit / 콜 한도 — 실유저 트래픽 못 버팀. | 개발·데모는 trial로 커버. 실배포 시 배포 시점 Cohere 정책에 따라 production 키로 전환. |
| 벤더 락인. | `RerankPort` 인터페이스로 격리 → Jina 등으로 교체 가능. |

## 13. 범위 밖 (측정 기반 후속)

- **retrieval 단순화** (5벡터 → BM25 + 1~2벡터). recall은 실측 문제 — 리랭커 도입 후 골든셋으로 각 벡터의 고유 recall 기여를 측정하고, 안 하는 벡터만 프룬. 이번 리팩터에 묶지 않는다(변경 격리).
- 인덱스에 `categoryName` 텍스트 필드 추가 (BM25 카테고리 매칭). 재색인 필요 — 별도.
- description을 리랭커 문서 텍스트에 포함할지.
