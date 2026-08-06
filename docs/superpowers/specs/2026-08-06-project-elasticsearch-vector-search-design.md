# 프로젝트 키워드 검색 Elasticsearch + 벡터(임베딩) 하이브리드 검색 설계

- 날짜: 2026-08-06
- 담당: 강대혁 (project-service)
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

**신규: `project-service/.../project/infrastructure/search/ProjectSearchRepository.java`**
- Spring Data Elasticsearch `ElasticsearchRepository<ProjectDocument, Long>` — 기본 CRUD/삭제용. 실제 하이브리드 쿼리는 `ElasticsearchOperations`로 직접 작성(레포지토리 쿼리 메서드로 표현하기엔 kNN+match 조합이 복잡함).

**신규: `project-service/.../project/application/port/ProjectSearchPort.java`** (인터페이스, 기존 `OrderPort` 같은 포트 패턴)
- `void index(Project project)` — title/summary/description으로 임베딩 생성 후 ES upsert
- `void remove(Long projectId)` — ES에서 삭제
- `List<Long> search(String keyword)` — nori match ∪ kNN 하이브리드 쿼리 실행, 매치된 projectId 목록 반환(빈 리스트 가능)

**신규: `project-service/.../project/infrastructure/search/ProjectSearchAdapter.java`** (`ProjectSearchPort` 구현체)
- `index`/`remove`: `ElasticsearchOperations` 직접 호출. 예외는 호출부(`ProjectServiceImpl`)로 던지지 않고 여기서 로그만 남기고 흡수한다(색인 실패가 원본 트랜잭션을 막으면 안 됨 — 아래 에러 처리 참고).
- `search`: ES 쿼리 실패/타임아웃 시 `ServiceUnavailableException`을 던진다. `CircuitBreakerFactory` fallback 메서드도 같은 예외를 던지는 용도로만 쓴다(다른 검색 경로로 강등하지 않음) — `OrderHttpClient` 등 기존 어댑터들의 fail-closed 패턴과 동일.

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

- `ProjectSearchAdapterTest`: ES를 Testcontainers로 띄워 실제 색인→검색 통합 테스트(nori 매치, kNN 매치, 매치 없음). OpenAI 임베딩 호출은 `EmbeddingModel`을 모킹.
- `ProjectServiceImplSearchTest`: `create`/`update`/`delete` 시 `searchPort.index`/`remove`가 호출되는지, 색인 어댑터가 예외를 던져도 트랜잭션이 성공하는지(모킹으로 검증).
- `ProjectServiceImplTest`(`findAll`): `keyword` 있을 때 `searchPort.search` 결과로 후보를 좁히는지, ES 실패 시 `ServiceUnavailableException`이 전파되는지, 빈 매치 시 즉시 빈 리스트 반환하는지.
- 기존 `findAll` role 가시성/categoryId/status/sort 테스트는 keyword 유무 두 경로 모두에서 동일하게 통과해야 한다(회귀 확인).

## 외부 의존성

- OpenAI API 키: 사용자 확인상 이미 존재. 실제 키가 `beadv7_7_earlybird_config`에 등록되어 있는지, 팀 내 다른 서비스가 이미 쓰고 있는 값인지는 구현 착수 시점에 재확인이 필요하다(이 문서 작성 시점에는 로컬에서 직접 확인할 방법이 없었음).
- Spring AI와 Spring Boot 4.1/Spring Cloud 2025.1.2의 버전 호환성은 구현 단계에서 검증한다(2026-08-06 기준 Spring AI가 Boot 4.1을 공식 지원하는지 확인 필요).

## 범위 밖

- ES 문서에 categoryId/status를 넣어 ES 쪽에서 직접 필터링하는 방식은 채택하지 않는다 — 위 아키텍처 절 참고(YAGNI, 이중 관리 방지).
- "관련도순" 정렬을 새 `ProjectSort` 옵션으로 추가하는 것은 이번 스코프에 포함하지 않는다 — 기존 3개 정렬 옵션(LATEST/DEADLINE/FUNDED_AMOUNT)만 유지.
- 별도 전용 벡터DB(Pinecone/Milvus/pgvector) 도입은 하지 않는다 — ES `dense_vector`로 충분하다고 판단(브레인스토밍 단계에서 합의).
- reward/board 등 다른 도메인의 검색 기능은 이번 설계 범위 밖이다.
