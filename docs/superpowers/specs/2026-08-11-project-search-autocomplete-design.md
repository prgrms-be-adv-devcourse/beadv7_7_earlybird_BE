# 프로젝트 제목 자동완성(prefix 검색) 설계

- 날짜: 2026-08-11
- 담당: 강대혁 (project-service)
- 구현 상태: 설계 완료, 구현 전
- 배경: 프로젝트 목록 화면의 검색창에 자동완성(타이핑 중 후보 제목 목록)을 추가하고 싶다. 기존 `GET /api/v1/projects?keyword=`는 nori 키워드 매치 + OpenAI 임베딩 kNN을 결합한 하이브리드 검색(`2026-08-06-project-elasticsearch-vector-search-design.md`)으로, 자동완성처럼 매 타이핑마다 호출하기엔 무겁고(임베딩 API 호출 포함) 반환 형태도 다르다(풀 `ProjectResponse` 목록). title 필드에 대한 ES prefix 쿼리로 가볍게 후보를 반환하는 전용 기능을 별도로 추가한다.

## 요구사항

- `title` 필드만 대상으로 한다(summary/description 제외) — 자동완성 목록은 "프로젝트 이름 제안"이 목적이라 결과가 깔끔해야 한다.
- prefix 쿼리는 기존 `title`(nori 분석, `korean_index`/`korean_search`) 필드를 그대로 쓴다 — 별도 `title.keyword` 서브필드나 매핑 변경을 추가하지 않는다.
- 검색어 최소 1자부터 반응한다. 최대 100자 제한은 기존 `findAll`의 keyword 파라미터와 동일하게 적용한다.
- 결과는 최대 10개, `projectId`와 `title`만 담는다.
- 매치가 없으면 빈 리스트를 반환한다(에러 아님).
- ES 장애/타임아웃 시 기존 `search()`와 동일하게 `ServiceUnavailableException`(503)을 던진다 — 별도 폴백 경로는 두지 않는다.
- 인증 불필요(비로그인 사용자도 호출 가능한 공개 API, 기존 `findAll`과 동일).

## 아키텍처

```
GET /api/v1/projects/autocomplete?keyword=xxx   (기존 GET /api/v1/projects와 별개 경로)
  → ProjectController.autocomplete()
    → ProjectService.autocomplete(keyword)
      → ProjectSearchPort.autocomplete(keyword)
        → ProjectSearchAdapter: ES title 필드에 prefix 쿼리(case_insensitive) 실행
          → List<ProjectSuggestion>(projectId, title) 반환, 최대 10개
```

**엔드포인트를 `/api/v1/projects`에 파라미터로 얹지 않고 별도 경로로 분리하는 이유**: 응답 스키마가 완전히 다르다(자동완성은 `{projectId, title}`, 기존 검색은 풀 `ProjectResponse`). 하나의 경로가 파라미터에 따라 다른 응답 스키마를 반환하는 것은 API 계약을 애매하게 만든다. 이미 이 컨트롤러에 같은 이유로 분리된 전례가 있다(`GET /api/v1/projects/me`) — "같은 리소스를 다른 목적으로 조회"할 때 쿼리 파라미터가 아니라 서브 경로로 분리하는 게 이 코드베이스의 기존 컨벤션이다.

**서킷브레이커는 기존 `projectSearch` id를 재사용한다** — 자동완성용 서킷브레이커를 새로 만들지 않는다. 자동완성과 하이브리드 검색은 둘 다 같은 ES 클러스터에 의존하므로 장애 도메인을 공유하는 게 맞고(ES가 죽으면 둘 다 죽어야 정상), 별도 설정 클래스를 추가하는 건 YAGNI에 어긋난다.

## 컴포넌트 변경

**변경: `project-service/.../project/application/port/ProjectSearchPort.java`**
- `List<ProjectSuggestion> autocomplete(String prefix)` 메서드 추가.
- `ProjectSuggestion(Long projectId, String title)` 레코드를 같은 패키지(`application/port`)에 신규 추가 — 포트가 `List<Long>`(기존 `search()`)과 달리 title도 함께 반환해야 하는 유일한 메서드라 전용 반환 타입이 필요하다.

**변경: `project-service/.../project/infrastructure/search/ProjectSearchAdapter.java`**
- `autocomplete(String prefix)` 구현 추가: `Query.of(q -> q.prefix(p -> p.field("title").value(prefix).caseInsensitive(true)))`로 prefix 쿼리 실행, `withMaxResults(10)`, 결과를 `ProjectSuggestion` 목록으로 매핑.
- 기존 `search()`와 동일하게 `circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID)`로 감싸고, fallback은 `ServiceUnavailableException`을 던진다.
- `case_insensitive: true`가 필요한 이유: `title` 필드의 분석기(`korean_index`/`korean_search`)에 `lowercase` 필터가 포함돼 있어 인덱스에는 소문자로 저장되는데, ES `prefix` 쿼리는 기본적으로 입력값을 분석하지 않고 그대로 비교한다 — 이 옵션 없이는 영문 제목에 대문자가 섞여 있을 때 매치가 안 될 수 있다.

**변경: `project-service/.../project/application/ProjectServiceImpl.java`** (및 `ProjectService` 인터페이스)
- `List<ProjectSuggestion> autocomplete(String keyword)` 추가 — `searchPort.autocomplete(keyword)` 결과를 그대로 반환(추가 가공 없음, categoryId/status 필터 없음).

**변경: `project-service/.../project/presentation/ProjectController.java`**
- `GET /api/v1/projects/autocomplete` 추가:
  ```java
  @GetMapping("/autocomplete")
  public List<ProjectAutocompleteResponse> autocomplete(
          @RequestParam @Size(min = 1, max = 100, message = "검색어는 1자 이상 100자 이하여야 합니다.") String keyword) {
      return projectService.autocomplete(keyword).stream()
              .map(s -> new ProjectAutocompleteResponse(s.projectId(), s.title()))
              .toList();
  }
  ```
- `GetMapping`은 기존 `findAll()`(`GET /api/v1/projects`)보다 위나 아래 아무 데나, 다만 `/{projectId}` 같은 path variable 매핑과 순서 충돌이 없는지만 확인(Spring이 정적 경로 `/autocomplete`를 `/{projectId}`보다 우선 매치하므로 실제로는 문제 없음, 기존 `/me`도 같은 패턴).

**신규: `project-service/.../project/presentation/dto/response/ProjectAutocompleteResponse.java`**
- `record ProjectAutocompleteResponse(Long projectId, String title) {}`
- `application/port`의 `ProjectSuggestion`을 컨트롤러가 그대로 반환하지 않고 이 프레젠테이션 DTO로 한 번 감싸는 이유: 기존 컨벤션상 컨트롤러는 항상 `presentation/dto/response`의 타입을 반환한다(`ProjectResponse`와 동일한 패턴) — 계층 간 타입을 섞지 않는다.

## 에러 처리

- ES 장애/타임아웃: 서킷브레이커 fallback → `ServiceUnavailableException` → 503 (기존 `search()`와 동일, 별도 폴백 없음).
- `keyword`가 빈 문자열이거나 100자 초과: `@Size(min=1, max=100)` 검증 실패 → 400 (Bean Validation, 기존 컨벤션).
- 매치 없음: 에러 아님, 빈 리스트 200 응답.

## 테스트

- `ProjectSearchAdapterTest`(기존 파일에 케이스 추가): prefix 매치 성공(대소문자 섞인 영문 제목 포함), 매치 없음(빈 리스트), 10개 초과 매치 시 10개로 절단, ES 장애 시 `ServiceUnavailableException` 전파.
- `ProjectServiceImplTest`: `autocomplete()`가 포트 결과를 그대로 전달하는지, ES 장애 시 예외가 그대로 전파되는지(가공/폴백 없음을 확인).
- 컨트롤러 레벨 별도 슬라이스 테스트는 만들지 않는다 — 기존 `findAll` 등 다른 엔드포인트도 같은 컨벤션(서비스 레이어에서 검증, 컨트롤러는 얇은 위임).

## 범위 밖

- `title.keyword` 서브필드 추가나 인덱스 매핑 변경은 하지 않는다 — 기존 분석 필드에 prefix 쿼리로 충분하다고 판단(브레인스토밍 단계에서 확인).
- summary/description을 자동완성 대상에 포함하는 것은 이번 스코프에 포함하지 않는다.
- 자동완성 결과에 categoryId/status 필터를 적용하는 것(예: 특정 카테고리 내에서만 자동완성)은 이번 스코프 밖 — 필요해지면 추후 별도 설계.
- 자동완성 전용 서킷브레이커/레이트리밋 도입은 하지 않는다 — 기존 `projectSearch` 서킷브레이커를 재사용한다(위 아키텍처 절 참고).
