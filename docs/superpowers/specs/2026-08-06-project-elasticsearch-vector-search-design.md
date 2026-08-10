# 프로젝트 키워드 검색 Elasticsearch + 벡터(임베딩) 하이브리드 검색 설계

- 날짜: 2026-08-06
- 담당: 강대혁 (project-service)
- 구현 상태: **완료 (2026-08-09)** — 설계대로 구현됐고, 구현하면서 새로 확인/결정된 세부사항은 아래 각 섹션에 그대로 반영해 뒀다.
- 배경: `findAll(keyword, categoryId, status, sort, requesterRole)`의 키워드 검색이 지금은 MySQL JPA `Specification`으로 `title`/`summary`에 `LIKE '%keyword%'`만 거는 수준이다(`ProjectServiceImpl.java:346-368`). 이 LIKE 구현은 ES 도입 전 임시로 만들어둔 스텁이라 버려도 된다(2026-08-06 사용자 확인) — ES로 완전히 대체하며, LIKE 경로를 폴백으로 유지할 필요는 없다. 인프라(`infrastructure/docker-compose.yml`, `infrastructure/elasticsearch/Dockerfile`)에는 nori 형태소분석기가 설치된 Elasticsearch 9.0.3 컨테이너가 이미 떠 있지만, project-service 코드에는 아직 전혀 연결되어 있지 않다. 이 키워드 검색을 ES 기반 키워드(nori) + 임베딩 벡터 하이브리드 검색으로 교체한다.

## 요구사항

- `findAll`의 기존 시그니처(`keyword, categoryId, status, sort, requesterRole`)와 응답 형태(`List<ProjectResponse>`)를 유지한다 — 컨트롤러/클라이언트 계약 변경 없음.
- `keyword`가 있을 때: nori 키워드 매치 + OpenAI 임베딩 kNN 벡터 검색을 함께 실행하는 하이브리드 검색으로 후보 프로젝트를 찾는다. 기존 LIKE 기반 매치는 완전히 대체되며 코드에서 제거한다.
- `keyword`가 없을 때: 기존 JPA `Specification` 경로 그대로 유지(변경 없음, ES 관여 없음).
- 기존 role 기반 가시성 규칙(ADMIN이 아니면 `PENDING_REVIEW`/`REJECTED` 항상 제외)과 `categoryId`/`status` 필터, `ProjectSort`(LATEST/DEADLINE/FUNDED_AMOUNT) 정렬은 키워드 검색 결과에도 **지금과 동일하게** 적용되어야 한다 — ES가 새로운 "관련도순" 정렬을 API에 추가하지는 않는다.
- ES가 다운되거나 느리면 키워드 검색은 명시적으로 실패한다(다른 포트 어댑터들과 동일하게 `ServiceUnavailableException` → 503) — LIKE 폴백 같은 별도 강등 경로는 두지 않는다(임시 스텁이었으므로).
- ES 인덱스가 MySQL과 어긋났을 때(장애 복구, 최초 도입) 전체를 다시 채워 넣을 수 있는 관리자용 재색인 경로가 있어야 한다.

## 아키텍처

MySQL을 계속 source of truth로 두고, ES는 순수하게 "키워드/벡터로 후보 projectId 집합을 찾아주는 검색 인덱스" 역할만 한다.

```
keyword 없음 → 기존 JPA Specification (변경 없음)

keyword 있음 → ES 하이브리드 쿼리(nori match ∪ kNN)로 candidateProjectIds 조회
             → candidateProjectIds를 기존 Specification(categoryId/status/role 필터)의
               in-절 조건으로 추가해 MySQL에서 최종 필터링 + ProjectSort 정렬
             → ProjectResponse 조립
```

핵심 결정: **ES 문서에는 categoryId/status를 넣지 않는다.** 그 필터링은 지금처럼 MySQL 쪽에서 candidateProjectIds에 대해 그대로 수행하므로, ES 쪽에서 이 필드들을 중복 저장/동기화할 이유가 없다(YAGNI). 이 덕분에 ES 재색인이 필요한 시점도 "검색 가능한 텍스트(title/summary/description)가 바뀔 때"로 좁혀진다 — `approve`/`reject`/`cancel`/`closeEarlyAsSucceeded`/`closeByDeadline`/`extendDeadline` 같은 상태·마감일 변경 메서드는 ES 재색인을 안 해도 된다.

임베딩은 Spring AI(`spring-ai-starter-model-openai`)의 `EmbeddingModel` 추상화를 쓴다 — 직접 OpenAI HTTP 클라이언트를 새로 작성하지 않는다(프레임워크 default 우선 원칙). ES 연동도 `spring-boot-starter-data-elasticsearch`(Spring Data Elasticsearch)의 `@Document`/`ElasticsearchOperations`를 쓴다.

## 컴포넌트 변경

**신규 의존성** (`project-service/build.gradle`)
- `implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'`
- `implementation 'org.springframework.ai:spring-ai-starter-model-openai'` (+ Spring AI BOM을 `dependencyManagement`에 추가 — Boot 4.1과 호환되는 최신 버전 확인 필요, 구현 단계에서 검증)

**신규: `project-service/.../project/infrastructure/search/ProjectDocument.java`**
- `@Document(indexName = "projects")`
- 필드: `projectId`(`@Id`), `title`/`summary`/`description`(nori 커스텀 분석기, `@Setting`으로 인덱스에 등록), `embedding`(`dense_vector`, `text-embedding-3-small` 1536차원, `similarity=cosine`)

**신규: `project-service/.../project/infrastructure/search/ProjectEmbeddingService.java`**
- AI 임베딩 모델(`EmbeddingModel`) 추상화 서비스. `@Autowired(required = false)`로 주입받아 API 키 미설정 시에도 기동 장애를 방지한다.
- `generateEmbedding(String text)`: **① `embeddingModel == null`이면 즉시 `null` 반환**(NPE 방지, 키 미설정 상황) → ② 텍스트 null/blank면 `null` 반환 → ③ OpenAI 토큰 초과 방지를 위해 입력 텍스트를 `MAX_TEXT_LENGTH = 2000`자로 절단(Truncation) → ④ `embeddingModel.embed(...)` 호출을 try-catch로 감싸 rate limit·네트워크 장애 등 어떤 예외가 나도 `WARN` 로그만 남기고 `null`을 반환. 이 4단계 순서 덕분에 "모델이 아예 없는 경우"와 "모델은 있는데 호출이 실패한 경우"가 모두 예외 없이 안전하게 `null`로 수렴하고, 호출부(색인/검색 양쪽)는 항상 "성공(벡터) 또는 실패(null, 키워드 폴백)" 두 가지만 신경 쓰면 된다.
- `generateEmbeddingForProject(project)`: title+summary+description을 합쳐서 위 `generateEmbedding`에 넘긴다. `isAvailable()`로 모델 존재 여부를 외부에 노출한다.

**변경: `project-service/.../project/domain/Project.java`** (Aggregate Root)
- 사전 계산된 임베딩 벡터를 담는 `embedding`(`float[]`) 필드와 `updateEmbedding(float[])` 메서드를 추가한다.
- **한 번 생성한 벡터는 재사용하고, 다시 계산하지 않는다.** `title`/`summary`/`description`이 수정될 때(`updateBeforePublish`/`updateAfterPublish`)만 `embedding`을 `null`로 되돌려서 "이제 이 프로젝트는 임베딩을 다시 만들어야 한다"는 신호로 쓴다. 즉 임베딩 생성은 프로젝트당 "최초 생성 시 1번" + "내용이 바뀔 때마다 1번"만 일어나고, 그 사이엔 저장된 값을 그대로 재사용한다.

**신규: `project-service/.../project/infrastructure/persistence/EmbeddingConverter.java`**
- JPA `AttributeConverter<float[], String>`. `float[]` ↔ JSON 문자열로 변환해 MySQL `projects.embedding`(`LONGTEXT`) 컬럼에 저장/복원한다. `ObjectMapper`의 체크 예외(`JsonProcessingException`)는 try-catch로 감싸 로그만 남기고 `null`을 반환한다(컨버터 인터페이스가 체크 예외를 선언하지 않으므로 필수).

**신규: `project-service/.../project/application/port/ProjectSearchPort.java`** (인터페이스, 기존 `OrderPort` 같은 포트 패턴)
- `void index(Project project)` — title/summary/description으로 임베딩 생성 후 ES upsert
- `void remove(Long projectId)` — ES에서 삭제
- `List<Long> search(String keyword)` — nori match ∪ kNN 하이브리드 쿼리 실행, 매치된 projectId 목록 반환(빈 리스트 가능)

**신규: `project-service/.../project/infrastructure/search/ProjectSearchAdapter.java`** (`ProjectSearchPort` 구현체)
- `index`/`remove`: `ProjectIndexRequestedEvent`, `ProjectRemovedFromIndexEvent` 비동기 이벤트를 발행한다. 실제 색인은 `ProjectSearchIndexEventListener`가 `AFTER_COMMIT` 시점에 DB에서 프로젝트를 재조회한 뒤 임베딩이 없으면 `ProjectEmbeddingService`로 생성하여 MySQL DB에 영속화(`updateEmbedding`)한 후 ES에 덮어쓴다.
- `search`: 검색어(`keyword`)에 대한 임베딩 벡터를 추출할 수 있는 경우 Nori 형태소 BM25 키워드 매칭(`title^2.0`, `summary^1.2`, `description`)과 kNN 벡터 코사인 유사도 쿼리(`embedding`, `k=10`, `numCandidates=100`, `boost=10.0f`)를 결합한 하이브리드 검색을 실행한다. 임베딩 불가능 시 Nori 키워드 매칭 전용 쿼리로 동작한다.
  - **쉽게 풀면:** 문서(프로젝트) 쪽 벡터는 위 "한 번 생성 후 재사용" 원칙대로 이미 ES에 저장돼 있는 값을 그대로 쓴다. 검색할 때 실시간으로 OpenAI를 부르는 건 **오직 사용자가 입력한 검색어 하나뿐**이다 — 검색어는 매번 새로운 텍스트라 미리 저장해 둘 수가 없어서, 이 부분만은 구조적으로 실시간 호출을 피할 수 없다. kNN 유사도 계산 자체는 "저장된 문서 벡터들 vs 방금 만든 검색어 벡터"를 비교하는 것이라, 문서 쪽 재사용 임베딩을 그대로 활용하는 게 맞다.
  - **`boost=10.0f`는 실측 검증된 값이 아니라 추정치다.** BM25 키워드 점수(보통 1~20+)와 코사인 유사도 점수(0~2)는 스케일이 완전히 달라서, 그냥 더하면 키워드 점수가 벡터 유사도를 압도해 버린다. `boost(10.0f)`로 벡터 점수 스케일을 대략 맞춰준 1차 보정이며, 실제 검색어로 "키워드는 다른데 의미는 비슷한" 케이스가 상위에 오는지 확인이 안 된 상태다. 필요하면 ES의 RRF(Reciprocal Rank Fusion) retriever로 전환하는 게 정석적인 해결책이다(점수 스케일과 무관하게 순위 기반으로 합산).
- ES 쿼리 실패/타임아웃 시 `ServiceUnavailableException`을 던진다. `CircuitBreakerFactory` fallback 메서드도 같은 예외를 던지는 용도로만 쓴다(다른 검색 경로로 강등하지 않음) — `OrderHttpClient` 등 기존 어댑터들의 fail-closed 패턴과 동일.

**변경: `project-service/.../project/application/ProjectServiceImpl.java`**
- 생성자에 `ProjectSearchPort searchPort` 주입
- `create`(58행), `update`(87행), `delete`(113행)에서 각각 저장/수정/삭제 성공 직후 `searchPort.index(project)` / `searchPort.remove(projectId)` 호출. 색인 자체가 실패해도 이 메서드들의 원본 트랜잭션·응답에는 영향 없음(어댑터가 예외를 흡수하므로).
- `findAll`: `keyword`가 있으면 `CircuitBreakerFactory`로 감싼 `searchPort.search(keyword)` 호출 → 결과 `candidateProjectIds`를 `buildSpecification`에 `projectId IN (...)` 조건으로 추가. ES가 죽어 있으면 `ServiceUnavailableException`이 그대로 컨트롤러까지 전파(503) — 강등 경로 없음.
- `candidateProjectIds`가 빈 리스트면(매치 없음) 그 즉시 빈 `List<ProjectResponse>` 반환(불필요한 MySQL 쿼리 생략).
- `buildSpecification`의 기존 `keyword` LIKE 분기(346-368행 부근)는 삭제한다 — 더 이상 쓰이지 않음. `buildSpecification`은 `categoryId`/`status`/role 가시성 필터 + (있다면) `candidateProjectIds` IN 조건만 담당하게 된다.

**신규: 관리자 재색인 엔드포인트**
- `ProjectService`/`ProjectServiceImpl`에 `void reindexAllProjects()` 추가 — 전체 프로젝트를 순회하며 `searchPort.index(project)` 재호출(백필/복구용, 기존 `reconcileFundedAmounts()`류 배치 메서드와 같은 성격).
- `ProjectInternalController`(내부 전용, `/internal/**`는 게이트웨이 라우트 없음)에 `POST /internal/v1/projects/reindex` 추가 — Eureka 직통 호출 또는 로컬 admin 트리거로만 실행.

## 에러 처리

- **색인(`index`/`remove`) 실패** (OpenAI 임베딩 API 에러, ES 다운 등): `ProjectSearchAdapter`가 예외를 잡아 `WARN` 로그(projectId, 원인)만 남기고 삼킨다. `create`/`update`/`delete`의 MySQL 트랜잭션·API 응답은 그대로 성공 처리된다 — 검색 인덱스는 최종 일관성(eventual consistency)으로 취급하고, 어긋난 상태는 `reindexAllProjects()`로 복구한다.
- **검색(`search`) 실패**: `findAll`에서 `ServiceUnavailableException`을 그대로 전파(503) — LIKE 기반 폴백 경로는 두지 않는다(그 코드는 임시 스텁이었으므로 함께 제거됨).
- **OpenAI 키/설정 누락**: 애플리케이션 기동은 막지 않는다. 색인 시점에 발생하면 위 "색인 실패" 경로(로그 후 흡수)로, 검색 시점에 발생하면 위 "검색 실패" 경로(`ServiceUnavailableException` 전파)로 처리된다 — 별도 분기 없이 각각 이미 정의된 에러 경로를 그대로 탄다. 키는 `beadv7_7_earlybird_config` 리포에 다른 서비스별 설정과 같은 방식으로 추가(구현 단계에서 실제 키 존재 여부 확인).

## 테스트

- `ProjectEmbeddingServiceTest`(신규): 모델 정상 동작(1536차원 벡터 생성) / `EmbeddingModel` 빈 자체가 없을 때 `null` 반환 / 모델이 예외를 던질 때 `null` 반환, 3가지 케이스를 검증한다.
- `ProjectSearchAdapterTest`: ES를 Testcontainers로 띄워 실제 색인→검색 통합 테스트(nori 매치, kNN 매치, 매치 없음). OpenAI 임베딩 호출은 `EmbeddingModel`을 모킹.
- `ProjectServiceImplSearchTest`: `create`/`update`/`delete` 시 `searchPort.index`/`remove`가 호출되는지, 색인 어댑터가 예외를 던져도 트랜잭션이 성공하는지(모킹으로 검증).
- `ProjectServiceImplTest`(`findAll`): `keyword` 있을 때 `searchPort.search` 결과로 후보를 좁히는지, ES 실패 시 `ServiceUnavailableException`이 전파되는지, 빈 매치 시 즉시 빈 리스트 반환하는지.
- 기존 `findAll` role 가시성/categoryId/status/sort 테스트는 keyword 유무 두 경로 모두에서 동일하게 통과해야 한다(회귀 확인).

## 외부 의존성

- OpenAI API 키: 사용자 확인상 이미 존재. 실제 키가 `beadv7_7_earlybird_config`에 등록되어 있는지, 팀 내 다른 서비스가 이미 쓰고 있는 값인지는 구현 착수 시점에 재확인이 필요하다(이 문서 작성 시점에는 로컬에서 직접 확인할 방법이 없었음).
- Spring AI와 Spring Boot 4.1/Spring Cloud 2025.1.2의 버전 호환성은 구현 단계에서 검증한다(2026-08-06 기준 Spring AI가 Boot 4.1을 공식 지원하는지 확인 필요).
- **`spring.elasticsearch.uris`도 `beadv7_7_earlybird_config`의 `project-service.yml`에 OpenAI 키와 같은 방식으로 추가해야 한다.** 로컬 `application.yml`에는 `spring.application.name`과 config-server import만 있고 실제 URI는 config repo가 공급하는 구조라(`CLAUDE.md` 참고), 이 값이 빠지면 Spring Boot의 ES 클라이언트가 기본값 `http://localhost:9200`으로 조용히 폴백한다 — docker-compose/실제 배포 환경에서는 이게 틀린 주소다(예: docker-compose 내부에서는 `http://elasticsearch:9200`). **이 설정 누락은 기동을 막지 않고 티도 안 난다:** `ProjectSearchIndexInitializer`는 인덱스 생성 실패를 WARN 로그로 흡수하고, `ProjectSearchAdapter.index()`/`remove()`도 실패를 WARN 로그로 흡수한다 — 눈에 보이는 유일한 증상은 키워드 검색이 원인 불명의 503(circuit breaker fallback)을 내거나, 그것도 아니면 그냥 빈 결과/이상한 결과를 조용히 돌려주는 것뿐이다. 배포 담당자는 에러가 없다고 정상 동작을 신뢰하지 말고, 이 값이 실제로 설정됐는지 명시적으로 확인해야 한다.

## 알려진 한계

- **결과 절단(truncation)이 categoryId/status 필터링보다 먼저 일어난다.** `ProjectSearchAdapter.MAX_RESULTS`(200)는 ES 인덱스 전체(categoryId/status 없음)에서 관련도 상위 200개를 자른 뒤에야 MySQL로 넘어가고, MySQL이 그다음에 categoryId/status/role 필터를 적용한다. 그래서 `GET /api/v1/projects?keyword=게임&categoryId=5`처럼 keyword+categoryId를 같이 쓰는 요청은, category-5에 실제로 매치되는 프로젝트가 있어도 그게 ES 전체 인덱스 기준 keyword 상위 200위 안에 못 들면 결과가 0건일 수 있다. non-ADMIN 요청에서는 PENDING_REVIEW/REJECTED 문서도 그 200개 후보 슬롯을 소비하고 나서야 MySQL에서 걸러지므로, 실효 결과 집합이 더 줄어들 수 있다. 이건 ES 문서에 categoryId/status를 안 넣기로 한 설계(YAGNI, 위 아키텍처 절 참고)의 자연스러운 귀결이지 구현 버그가 아니지만, 기존 LIKE 검색 대비 실제로 체감되는 동작 차이라 여기 기록해 둔다. `MAX_RESULTS`를 올리면 이 문제가 일어나는 빈도는 줄지만, 근본적으로 없어지지는 않는다(어차피 top-N 자르기 자체가 categoryId/status를 모르는 채로 일어나므로).
- **`reindexAllProjects()`는 추가(additive)만 하고 고아 문서를 지우지 않는다.** MySQL에서 이미 삭제된 프로젝트의 ES 문서가 남아 있어도, 전체 재색인은 현재 프로젝트들을 다시 upsert할 뿐 ES에만 남아 있는 고아 문서는 정리하지 않는다. 인지된 한계로 남겨둔다(이번 수정 범위에 포함하지 않음).
- **대량 재색인 시 OpenAI 동시 호출 제한이 없다.** `reindexAllProjects()`가 임베딩이 없는 프로젝트 전체에 대해 색인 이벤트를 한꺼번에 발행하면, `@Async` 리스너가 프로젝트마다 독립적으로 병렬 OpenAI 호출을 시도한다. 데이터가 적을 때(지금 시드 데이터 수준)는 문제없지만, 실제 운영 데이터로 규모가 커지면 OpenAI rate limit(429)에 걸릴 수 있다. 평소 개별 생성/수정 흐름(프로젝트당 이벤트 1건)에서는 발생하지 않고, "전체 재색인" 버튼을 대량 데이터에 쓸 때만 해당하는 문제다. 후속 개선 방향: Spring Batch 청크 분할(예: 50건 단위) 또는 스레드풀 동시성 제한 + `@Retryable` 백오프.
- **`boost=10.0f`는 아직 실측 검증되지 않은 값이다.** 위 "컴포넌트 변경 > ProjectSearchAdapter" 절 참고 — BM25/코사인 유사도 스케일 차이를 대충 맞춘 값이라, 실제 하이브리드 랭킹 품질은 별도로 확인이 필요하다.

## 구현 중 발견된 함정 (참고용, 재발 방지)

구현 과정에서 한 차례, `embedding`이 `null`일 때 실제 AI 모델을 부르는 대신 `new Random(text.hashCode())`로 만든 **의사난수 벡터를 임시로 채워 넣는 코드**가 잠깐 들어간 적이 있다. 텍스트 내용과 전혀 무관한 랜덤 값이라 의미적으로는 완전히 가짜인데, 배열 길이가 항상 1536이라 "임베딩이 비어있는지" 체크하는 로직을 그냥 통과해 버리고 진짜 벡터처럼 ES에 색인된다는 게 문제였다. 이 상태로 두면 나중에 진짜 모델을 붙일 때 `WHERE embedding IS NOT NULL` 같은 조건으로 "이미 벡터가 있는 행은 건너뛰기"를 하는 순간, 가짜 랜덤 벡터를 진짜인 줄 알고 영구적으로 놓치는 조용한 데이터 오염이 생긴다. 발견 즉시 제거했고, 지금은 실제 모델 호출이 실패하면 그냥 `null`을 유지한다(위 `ProjectEmbeddingService` 참고). **앞으로 비슷한 "임시로 그럴듯한 값 채워두기" 식의 스텁이 필요해지더라도, `null`과 구분이 안 되는 값으로 채우면 안 된다** — 차라리 명시적으로 `null`로 남겨두는 편이 안전하다.

## 범위 밖

- ES 문서에 categoryId/status를 넣어 ES 쪽에서 직접 필터링하는 방식은 채택하지 않는다 — 위 아키텍처 절 참고(YAGNI, 이중 관리 방지).
- "관련도순" 정렬을 새 `ProjectSort` 옵션으로 추가하는 것은 이번 스코프에 포함하지 않는다 — 기존 3개 정렬 옵션(LATEST/DEADLINE/FUNDED_AMOUNT)만 유지.
- 별도 전용 벡터DB(Pinecone/Milvus/pgvector) 도입은 하지 않는다 — ES `dense_vector`로 충분하다고 판단(브레인스토밍 단계에서 합의).
- reward/board 등 다른 도메인의 검색 기능은 이번 설계 범위 밖이다.
