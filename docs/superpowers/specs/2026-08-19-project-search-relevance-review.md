# 프로젝트 검색 연관도(무관한 결과) 리뷰

- 날짜: 2026-08-19
- 담당: 강대혁 (project-service)
- 구현 상태: **완료 (2026-08-19)** — 1·2번 수정 적용, 3·5번은 대응 안 하기로 결정, 4번은 회귀 테스트 추가로 해소
- 관련 문서: [2026-08-06-project-elasticsearch-vector-search-design.md](./2026-08-06-project-elasticsearch-vector-search-design.md), [2026-08-11-project-search-autocomplete-design.md](./2026-08-11-project-search-autocomplete-design.md)

## 배경

TODO

## 확인된 사실

TODO

## 문제점

### 1. minimum_should_match / min_score 부재

`ProjectSearchAdapter.doSearch()`(`:123-134`)는 title match(boost 2.0) / summary match(boost 1.2) /
description match / kNN을 전부 `should`로만 묶고 `must`는 하나도 없다. ES bool 쿼리는 `should`만 있으면
기본 `minimum_should_match=1`이라 네 조건 중 아무거나 하나만 걸려도 매치된다. 게다가 `match` 쿼리는
기본 연산자가 OR라 검색어가 여러 단어면 그중 한 단어만 겹쳐도 그 clause가 통과한다. `min_score` 설정도
없어서 약하게 매치된 문서도 그대로 후보(`candidateProjectIds`)에 포함된다 — 코드 전체에
`minimum_should_match`/`min_score`/`minScore` 문자열이 한 번도 등장하지 않음(grep 확인).

kNN clause 자체는 `.similarity(0.78f)`(`:133`) 하한이 있어 의미적으로 무관한 문서를 걸러내지만, 이건
should 중 하나일 뿐이라 title/summary/description match 세 clause 중 아무거나 하나만 약하게 걸려도
kNN 문턱과 무관하게 결과에 포함된다.

**실측 (2026-08-19, 로컬 docker 환경, OPENAI_API_KEY 연결 후):**

"고양이 자동 급식기 프로젝트"(projectId 47)와 완전히 무관한 통제군 "전자책 자가출판 플랫폼"(projectId
48)을 만들고 "냥이"로 검색한 결과, 47/48뿐 아니라 기존의 전혀 무관한 프로젝트(`수제 가죽 노트커버`,
`강대혁의 시리즈 모음집`, `얼리버드 로고` 등 10개 이상)가 전부 함께 매치됐다.

원인을 `_analyze` API로 직접 확인:
```
GET /projects/_analyze {"analyzer":"korean","text":"냥이"}
→ tokens: ["냥", "이"]
```
nori 사전에 "냥이"가 표준 명사로 없어(속어라 미등재) 미등록 단어(OOV) 처리 로직이 음절 단위로 쪼갠다.
"이"는 한국어 조사/음절로 거의 모든 문장에 등장하므로, `match`(기본 OR)가 "이" 하나만으로도 통과된다.

키워드 clause만으로 `_search`를 직접 돌려본 실제 점수:
```
5.485  수제 가죽 노트커버          ← 완전 무관, 1위
2.789  강대혁의 시리즈 모음집       ← 완전 무관, 2위
1.057  고양이 자동 급식기 프로젝트  ← 진짜 타겟, 3위
1.035  전자책 자가출판 플랫폼       ← 무관 통제군, 4위 (타겟과 0.02 차이, 사실상 동률)
```
무관한 문서가 타겟보다 5배 높은 점수로 1위를 차지한다. 이 사례는 동의어/임베딩 문제이기 전에 "미등록
속어가 흔한 조사 한 글자로 분해되는 nori 특성"과 "minimum_should_match 없는 OR 매치"가 겹쳐 생기는
훨씬 노골적인 결함이다.

### 2. 검색 결과가 관련도가 아닌 DB 정렬 기준으로 재정렬됨

`search()`는 ES가 계산한 relevance score를 버리고 `projectId` 리스트만 반환한다(`:147`
`hits.stream().map(hit -> hit.getContent().projectId()).toList()`). `ProjectServiceImpl.findAll()`
(`:82-95`)은 이 candidateProjectIds를 JPA `Specification`의 `IN` 조건으로만 쓰고, 최종 정렬은
`ProjectSort`(`LATEST`/`DEADLINE`/`FUNDED_AMOUNT`, 기본값 `LATEST`)로 한다 — "관련도순" 정렬 옵션 자체가
`ProjectSort` enum에 없다. 그 결과 ES가 약한 점수로 겨우 매치시킨 문서가 최신순 정렬 때문에 1페이지
맨 위로 올라오고, 정작 강하게 매치된(점수 높은) 문서가 뒤 페이지로 밀릴 수 있다.

### 3. 동의어(속어 포함) 처리가 임베딩 하나에만 의존

`infrastructure/elasticsearch/Dockerfile`엔 nori 플러그인만 설치돼 있고, `project-index-settings.json`의
`korean` analyzer는 `nori_tokenizer` 단독 구성이다 — synonym filter도, 사전 파일도 코드베이스 어디에도
없다(grep 확인). 그래서 "냥이"→"고양이"류의 동의어/속어 매핑은 사전 기반으로는 전혀 처리되지 않고,
kNN 의미 검색 하나에만 의존한다. 문제는 이게 사전처럼 결정적(deterministic)이지 않고 확률적이라는
점이다 — 임베딩 모델이 그 속어를 실제로 학습해뒀는지, 코사인 유사도가 하필 0.78 문턱을 넘는지에
결과가 좌우된다. 게다가 로컬 개발 환경은 `OPENAI_API_KEY`가 `.env.example`에 항목조차 없어서
(`infrastructure/docker-compose.yml:310` `OPENAI_API_KEY: ${OPENAI_API_KEY:-}` → 기본값 빈 문자열)
로컬에서는 이 kNN 경로 자체가 조용히 비활성화돼 있을 가능성이 높다 — 즉 로컬에서는 "냥이"로 검색하면
키워드도 벡터도 안 걸려 아무것도 안 나올 수 있음.

**결정(2026-08-19): 별도 동의어 사전 안 만듦.** OpenAI 임베딩(kNN 의미 검색)으로 계속 대응.

**추가 실측 (2026-08-19, 아래 6번 모델 교체 이후):** 임베딩 모델을 `text-embedding-3-small`로
바꾼 뒤 "냥이"↔"고양이 자동 급식기" 실제 코사인 유사도를 직접 재보니 **0.208**로, 0.78 문턱에
한참 못 미친다. 즉 모델을 제대로 된 것으로 바꿔도 "냥이"는 여전히 "고양이"를 못 찾는다 — 이
문서에 적어둔 "임베딩이 속어를 확률적으로 잡아줄 것"이라는 기대는 이 케이스에서 틀렸다. 다만
무관한 문서가 뜨는 것보다는(false positive) 아무것도 안 뜨는 게(false negative) 훨씬 안전하므로,
3번 결정 자체를 뒤집을지는 별도 판단 필요.

### 4. 테스트 커버리지 공백

`ProjectSearchAdapterIntegrationTest.index_then_search_findsByKeyword`(같은 이름의 테스트 파일)는
검색어와 **겹치는 단어가 하나도 없는** 두 프로젝트만으로 "무관한 문서는 안 나온다"를 검증한다. 실제
우려 지점인 "단어 하나만 겹치는(하지만 주제는 무관한) 프로젝트가 뜨는가"는 어떤 테스트도 다루지 않는다
— 문제 1에서 설명한 구멍이 CI로 걸러지지 않는 이유. 부수적으로 같은 파일 37번 줄 주석은 "kNN 유사도
하한(0.5)"라고 적혀 있는데 실제 코드(`ProjectSearchAdapter.java:133`)는 `0.78`이라 주석이 stale하다.

### 5. 임베딩 생성 실패 시 영구 null — 재시도 스케줄러 없음

생성/수정 시 `Project.embedding`이 null이면 `ProjectSearchIndexEventListener.onIndexRequested()`
(`:52-69`)가 `embeddingService.generateEmbeddingForProject()`로 OpenAI를 호출해 채운다. 이 호출이
실패하면(레이트리밋, 일시 장애, 키 누락 등) `embedding != null` 조건(`:60`)을 못 만족해 DB 저장을
건너뛰고, `adapter.applyIndex(project)`(`:68`)는 embedding 없는 상태 그대로 ES에 색인한다 — 이후 그
프로젝트는 키워드 매치로만 검색되고 의미 검색 대상에서 영구히 빠진다. `project.getEmbedding() == null`
문서를 다시 채워주는 유일한 경로는 `ProjectSearchAdapter.doBulkIndex()`(관리자 수동 재색인)뿐이고,
project-service의 `@Scheduled` 잡은 `FundedAmountReconciliationScheduler`/`ProjectDeadlineScheduler`
둘뿐이라 임베딩 실패를 자동으로 재시도해주는 경로가 없다(grep 확인).

**결정(2026-08-19): 대응 안 함.** 수동 벌크 재색인 경로가 이미 있고 발생 빈도도 낮아 자동 재시도
스케줄러까지는 우선순위 낮다고 판단.

### 6. 임베딩 모델 미지정 → 구형 text-embedding-ada-002 기본값 사용, 유사도 문턱 무력화

`project-service.yml`이 `spring.ai.openai.embedding.options.model`을 명시하지 않아서 Spring AI
(`spring-ai-openai:2.0.0`)의 `OpenAiEmbeddingOptions.DEFAULT_EMBEDDING_MODEL`(디컴파일로 확인:
`EmbeddingModel.TEXT_EMBEDDING_ADA_002`)이 그대로 쓰이고 있었다. `text-embedding-ada-002`는
짧고 무관한 텍스트끼리도 코사인 유사도가 0.7~0.9대로 뭉치는 걸로 잘 알려진 구형 모델이라,
`ProjectSearchAdapter`의 kNN 유사도 하한(0.78)이 사실상 필터링 기능을 못 했다.

**실측:** "냥이"와 완전 무관한 텍스트("강대혁 커피 마시는 영상" + 의미없는 자모 나열) 간 코사인
유사도가 ada-002로는 **0.823**(문턱 통과) — 실제로 1번 문제의 minimum_should_match 수정을
적용한 뒤에도 무관한 프로젝트(`ㅇㅇ`, `테스트3`, `강대혁 테스트`, `강대혁 커피 마시는 영상`)가
kNN을 통해 여전히 검색 결과에 남아있었고, 이게 원인이었다. `text-embedding-3-small`(같은
1536차원)로는 같은 텍스트 쌍 유사도가 **0.208**로 정상적으로 낮게 나온다.

**조치 (2026-08-19, 완료):** `beadv7_7_earlybird_config`의 `project-service.yml`에
`spring.ai.openai.embedding.options.model: text-embedding-3-small` 명시(커밋 `fd7ffa1`).
기존에 저장된 임베딩(ada-002 기반)은 새 모델과 비교 불가능해서, 로컬에서 전체 프로젝트의
`embedding` 컬럼을 null로 초기화하고 `/api/v1/projects/reindex`로 재생성해서 검증함(서킷브레이커
시간제한 때문에 3~6개씩 배치로 나눠 처리). 배포 환경에도 같은 마이그레이션(전체 재색인) 필요.

## 개선 방안 (검토 필요)

TODO

## 결정 사항

TODO
