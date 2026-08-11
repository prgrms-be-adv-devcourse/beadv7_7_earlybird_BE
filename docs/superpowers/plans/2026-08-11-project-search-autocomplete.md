# 프로젝트 제목 자동완성(prefix 검색) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/v1/projects/autocomplete?keyword=` 엔드포인트를 추가해, title 필드에 대한 ES prefix 쿼리로 최대 10개의 `{projectId, title}` 자동완성 후보를 반환한다.

**Architecture:** 기존 하이브리드 검색(`ProjectSearchPort.search`)과 같은 포트/어댑터 계층에 `autocomplete` 메서드를 나란히 추가한다. `ProjectController` → `ProjectServiceImpl` → `ProjectSearchPort` → `ProjectSearchAdapter`(ES 호출) 순으로 위임하며, 기존 `projectSearch` 서킷브레이커를 그대로 재사용한다.

**Tech Stack:** Spring Boot 4.1, Spring Data Elasticsearch(`ElasticsearchOperations`, `co.elastic.clients` Query DSL), Resilience4j(`CircuitBreakerFactory`), JUnit 5 + Mockito + AssertJ, Testcontainers(ES 통합 테스트).

**스펙:** `docs/superpowers/specs/2026-08-11-project-search-autocomplete-design.md`

## Global Constraints

- 새 의존성을 추가하지 않는다 — 기존 `spring-boot-starter-data-elasticsearch`, `resilience4j` 등만 사용한다.
- ES 인덱스 매핑/설정 파일(`project-index-mapping.json`, `project-index-settings.json`)은 변경하지 않는다 — 기존 `title` 필드(nori 분석) 그대로 prefix 쿼리 대상.
- 자동완성 전용 서킷브레이커를 새로 만들지 않는다 — `ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID`("projectSearch")를 재사용한다.
- 컨트롤러 레이어에는 별도 슬라이스 테스트를 추가하지 않는다 — 기존 컨벤션대로 서비스/어댑터 레이어에서 검증한다.
- 응답은 최대 10개, `title` 필드만 대상(summary/description 제외), 매치 없으면 빈 리스트(에러 아님), ES 장애 시 `ServiceUnavailableException`(503) — 폴백 경로 없음.
- 모든 커밋 메시지는 이 저장소의 `Feat:`/`Fix:`/`Test:` 등 접두사 컨벤션을 따른다.

---

### Task 1: `ProjectSearchPort`/`ProjectSearchAdapter` — 자동완성 핵심 로직

**Files:**
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSuggestion.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSearchPort.java` (21번째 줄, `search` 메서드 뒤)
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapter.java` (상단 상수, 클래스 끝에 메서드 추가)
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterTest.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterIntegrationTest.java`

**Interfaces:**
- Produces: `ProjectSuggestion(Long projectId, String title)` record (패키지: `application.port`). `ProjectSearchPort.autocomplete(String prefix): List<ProjectSuggestion>` — 매치 없으면 빈 리스트, ES 장애 시 `ServiceUnavailableException`. Task 2/3이 이 시그니처를 그대로 사용한다.

- [ ] **Step 1: `ProjectSuggestion` 레코드 생성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSuggestion.java`:

```java
package com.growmighty.lectures.firstday.project.project.application.port;

public record ProjectSuggestion(Long projectId, String title) {
}
```

- [ ] **Step 2: `ProjectSearchPort`에 `autocomplete` 시그니처 추가, `ProjectSearchAdapter`에 임시 스텁 구현**

`ProjectSearchPort.java` — 기존 `search` 메서드(21번째 줄) 바로 뒤에 추가:

```java
    /** title prefix 매치(자동완성). 매치 없으면 빈 리스트. ES 장애 시 ServiceUnavailableException. */
    List<ProjectSuggestion> autocomplete(String prefix);
```

`ProjectSearchAdapter.java` — 인터페이스를 구현해야 컴파일되므로, 클래스 끝(마지막 `}` 앞)에 우선 자리표시용 구현을 추가한다:

```java
    @Override
    public List<ProjectSuggestion> autocomplete(String prefix) {
        throw new UnsupportedOperationException("not yet implemented");
    }
```

- 새 import 추가: `com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion` (`ProjectSearchAdapter.java` 상단, 기존 `ProjectSearchPort` import 근처).
- 이 시점에 `./gradlew :project-service:compileJava`가 통과하는지 확인한다(컴파일 확인용, 테스트 실행 아님).

- [ ] **Step 3: 실패하는 테스트 작성**

`ProjectSearchAdapterTest.java` 맨 아래(`search_elasticsearchCallFailure_throwsServiceUnavailable` 뒤, 클래스 닫는 `}` 앞)에 추가. 파일 상단 import에 `com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion` 한 줄 추가.

```java
    @Test
    @DisplayName("자동완성이 성공하면 매치된 문서들의 projectId와 title을 반환한다")
    @SuppressWarnings("unchecked")
    void autocomplete_success_returnsSuggestions() {
        SearchHits<ProjectDocument> hits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(new ProjectDocument(42L, "카카오 프로젝트", null, null, new float[1536]));
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(hits);

        List<ProjectSuggestion> result = adapter.autocomplete("카카");

        assertThat(result).containsExactly(new ProjectSuggestion(42L, "카카오 프로젝트"));
    }

    @Test
    @DisplayName("자동완성 호출이 실패하면 503 예외로 변환한다")
    void autocomplete_elasticsearchCallFailure_throwsServiceUnavailable() {
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenThrow(new RuntimeException("es down"));

        assertThatThrownBy(() -> adapter.autocomplete("카카"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("매치되는 문서가 없으면 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void autocomplete_noMatches_returnsEmptyList() {
        SearchHits<ProjectDocument> hits = mock(SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(hits);

        List<ProjectSuggestion> result = adapter.autocomplete("존재안함");

        assertThat(result).isEmpty();
    }
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `./gradlew :project-service:test --tests "ProjectSearchAdapterTest.autocomplete_success_returnsSuggestions"`
Expected: FAIL — `UnsupportedOperationException: not yet implemented` (Step 2의 스텁이 그대로 실행됨).

- [ ] **Step 5: 실제 구현으로 교체**

`ProjectSearchAdapter.java` — `MAX_RESULTS` 상수 근처에 새 상수 추가:

```java
    private static final int AUTOCOMPLETE_MAX_RESULTS = 10;
```

Step 2의 스텁 메서드를 아래로 교체:

```java
    @Override
    public List<ProjectSuggestion> autocomplete(String prefix) {
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> doAutocomplete(prefix),
                this::autocompleteFallback);
    }

    private List<ProjectSuggestion> doAutocomplete(String prefix) {
        Query query = Query.of(q -> q.prefix(p -> p.field("title").value(prefix).caseInsensitive(true)));
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(AUTOCOMPLETE_MAX_RESULTS)
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream()
                .map(hit -> new ProjectSuggestion(hit.getContent().projectId(), hit.getContent().title()))
                .toList();
    }

    private List<ProjectSuggestion> autocompleteFallback(Throwable cause) {
        log.warn("프로젝트 자동완성 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectSearchAdapterTest"`
Expected: PASS (기존 케이스 포함 전체).

- [ ] **Step 7: 단위 테스트 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSuggestion.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSearchPort.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapter.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterTest.java
git commit -m "Feat: 프로젝트 제목 자동완성 prefix 쿼리 어댑터 구현"
```

- [ ] **Step 8: ES 통합 테스트 추가 (prefix 매치 + 대소문자 무시 + 10개 제한)**

`ProjectSearchAdapterIntegrationTest.java` 맨 아래(`remove_thenNotFoundBySearch` 뒤, 클래스 닫는 `}` 앞)에 추가. 파일 상단 import에 `com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion` 한 줄 추가.

```java
    @Test
    @DisplayName("prefix로 시작하는 제목만 자동완성 후보로 나온다")
    void autocomplete_matchesTitlePrefix() {
        Project matching = savedProject("카카오 프로젝트");
        Project other = savedProject("완전히 다른 프로젝트");
        adapter.index(matching);
        adapter.index(other);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ProjectSuggestion> result = adapter.autocomplete("카카");
            assertThat(result).extracting(ProjectSuggestion::projectId).contains(matching.getProjectId());
            assertThat(result).extracting(ProjectSuggestion::projectId).doesNotContain(other.getProjectId());
        });
    }

    @Test
    @DisplayName("영문 제목은 대소문자와 무관하게 매치된다")
    void autocomplete_caseInsensitive() {
        Project project = savedProject("Kakao Project");
        adapter.index(project);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ProjectSuggestion> result = adapter.autocomplete("kakao");
            assertThat(result).extracting(ProjectSuggestion::projectId).contains(project.getProjectId());
        });
    }

    @Test
    @DisplayName("매치가 10개를 넘으면 10개로 잘린다")
    void autocomplete_limitsToTenResults() {
        for (int i = 0; i < 12; i++) {
            adapter.index(savedProject("접두어테스트 프로젝트 " + i));
        }

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.autocomplete("접두어테스트")).hasSize(10));
    }
```

- [ ] **Step 9: 통합 테스트 실행 → 통과 확인 (Docker 필요)**

Run: `./gradlew :project-service:test --tests "ProjectSearchAdapterIntegrationTest"`
Expected: PASS. (Docker가 안 떠 있으면 Testcontainers가 실패한다 — `docs/1_LOCAL_DB_SETUP.md` 참고해 Docker 먼저 확인.)

- [ ] **Step 10: 통합 테스트 커밋**

```bash
git add project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterIntegrationTest.java
git commit -m "Test: 자동완성 prefix/대소문자/결과 제한 통합 테스트 추가"
```

---

### Task 2: `ProjectService`/`ProjectServiceImpl` — 서비스 레이어 위임

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java` (23번째 줄, `findAll` 뒤)
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java` (81번째 줄, `findAll` 메서드 뒤)
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplAutocompleteTest.java` (신규)

**Interfaces:**
- Consumes: Task 1의 `ProjectSearchPort.autocomplete(String prefix): List<ProjectSuggestion>`.
- Produces: `ProjectService.autocomplete(String keyword): List<ProjectSuggestion>` — Task 3의 컨트롤러가 이 시그니처를 그대로 호출한다.

- [ ] **Step 1: `ProjectService` 인터페이스에 시그니처 추가**

`ProjectService.java` 상단에 import 추가:

```java
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
```

기존 `findAll` 메서드(23번째 줄) 바로 뒤에 추가:

```java
    /** title prefix 매치(자동완성). 매치 없으면 빈 리스트. ES 장애 시 ServiceUnavailableException. */
    List<ProjectSuggestion> autocomplete(String keyword);
```

- [ ] **Step 2: `ProjectServiceImpl`에 구현 추가**

`ProjectServiceImpl.java` 상단에 import 추가:

```java
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
```

기존 `findAll` 메서드(81번째 줄) 바로 뒤, `findById` 앞에 추가:

```java
    @Override
    public List<ProjectSuggestion> autocomplete(String keyword) {
        return searchPort.autocomplete(keyword);
    }
```

이 시점에 `./gradlew :project-service:compileJava`가 통과하는지 확인한다.

- [ ] **Step 3: 실패하는 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplAutocompleteTest.java` 신규 생성 — 기존 `ProjectServiceImplFindAllSearchTest.java`와 동일한 mock 구성을 따른다:

```java
package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceImplAutocompleteTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);
    private final ProjectSearchPort searchPort = mock(ProjectSearchPort.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> selfProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RewardService> rewardServiceProvider = mock(ObjectProvider.class);

    private ProjectServiceImpl projectService;

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
    }

    @Test
    @DisplayName("검색 포트가 반환한 자동완성 후보를 그대로 전달한다")
    void autocomplete_delegatesToSearchPort() {
        when(searchPort.autocomplete("카카")).thenReturn(List.of(new ProjectSuggestion(1L, "카카오 프로젝트")));

        List<ProjectSuggestion> result = projectService.autocomplete("카카");

        assertThat(result).containsExactly(new ProjectSuggestion(1L, "카카오 프로젝트"));
    }

    @Test
    @DisplayName("ES 장애 시 폴백 없이 503이 그대로 전파된다")
    void autocomplete_searchFails_propagatesServiceUnavailable() {
        when(searchPort.autocomplete("카카")).thenThrow(new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다."));

        assertThatThrownBy(() -> projectService.autocomplete("카카"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectServiceImplAutocompleteTest"`
Expected: PASS. (Step 2에서 구현을 먼저 넣었으므로 바로 통과해야 한다 — 만약 실패하면 Step 2의 위임 로직을 다시 확인.)

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplAutocompleteTest.java
git commit -m "Feat: ProjectService에 자동완성 위임 메서드 추가"
```

---

### Task 3: `ProjectController` — 엔드포인트 노출

**Files:**
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/dto/response/ProjectAutocompleteResponse.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectController.java` (63번째 줄, `findAll` 메서드 뒤)

**Interfaces:**
- Consumes: Task 2의 `ProjectService.autocomplete(String keyword): List<ProjectSuggestion>`.
- Produces: `GET /api/v1/projects/autocomplete?keyword=` — 응답 `List<ProjectAutocompleteResponse>` (`{projectId, title}`).

- [ ] **Step 1: 응답 DTO 생성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/dto/response/ProjectAutocompleteResponse.java`:

```java
package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;

public record ProjectAutocompleteResponse(Long projectId, String title) {
    public static ProjectAutocompleteResponse from(ProjectSuggestion suggestion) {
        return new ProjectAutocompleteResponse(suggestion.projectId(), suggestion.title());
    }
}
```

- [ ] **Step 2: 컨트롤러에 엔드포인트 추가**

`ProjectController.java` 상단에 import 추가:

```java
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectAutocompleteResponse;
```

기존 `findAll` 메서드(63번째 줄) 바로 뒤, `findMyProjects` 앞에 추가:

```java
    /**
     * 제목(title) 자동완성. 하이브리드 검색(GET /api/v1/projects?keyword=)과 달리 title에 대한
     * prefix 매치만 가볍게 수행하고, 최대 10개까지 {projectId, title}만 반환한다.
     */
    @GetMapping("/autocomplete")
    public List<ProjectAutocompleteResponse> autocomplete(
            @RequestParam @Size(min = 1, max = 100, message = "검색어는 1자 이상 100자 이하여야 합니다.") String keyword) {
        return projectService.autocomplete(keyword).stream()
                .map(ProjectAutocompleteResponse::from)
                .toList();
    }
```

- [ ] **Step 3: 전체 모듈 테스트 실행 — 컴파일/회귀 확인**

이 컨트롤러는 기존 컨벤션상 별도 슬라이스 테스트가 없다(스펙 문서 참고) — 전체 테스트 스위트로 컴파일과 회귀만 확인한다.

Run: `./gradlew :project-service:test`
Expected: PASS (기존 테스트 전부 포함, 새로 추가한 Task 1/2 테스트도 포함).

- [ ] **Step 4: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/dto/response/ProjectAutocompleteResponse.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectController.java
git commit -m "Feat: 프로젝트 제목 자동완성 API 엔드포인트 추가"
```

---

## 완료 후 확인

- `GET /api/v1/projects/autocomplete?keyword=카` 를 로컬(`project-service` + ES 컨테이너 기동 상태)에서 curl/Swagger로 직접 호출해 응답 형태(`[{ "projectId": ..., "title": ... }]`)를 눈으로 확인한다.
- `docs/superpowers/specs/2026-08-11-project-search-autocomplete-design.md`의 "이번에 만들 것"/"이번엔 안 하는 것" 항목과 실제 구현이 어긋나지 않는지 마지막으로 대조한다.
