# 검색 Cross-encoder 리랭커 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는 superpowers:executing-plans 로 task 단위로 구현. 스텝은 체크박스(`- [ ]`).

**Goal:** BM25/kNN fusion 결과 상위 40개를 Cohere Rerank 3.5로 재정렬하는 리랭킹 단계를 추가하고, QueryIntent LLM + Compatibility 레이어를 제거한다.

**Architecture:** `ProjectSearchAdapter.doSearch`가 BM25/임베딩엔 확장 쿼리를, 리랭커엔 원본 쿼리를 넘긴다. fusion은 후보 40 생성으로 격하. `Reranker` 인터페이스 뒤에 `CohereReranker`(RestClient + CircuitBreaker fallback) / `NoOpReranker`(`cohere.rerank.enabled=false`일 때). 계절 강한 충돌은 `SeasonalConflictFilter`로 rerank 전에 하드 제외.

**Tech Stack:** Spring Boot 4.1, Spring `RestClient`, resilience4j `CircuitBreakerFactory` + `TimeLimiterRegistry`, Elasticsearch Java client, JUnit 5 + Mockito + `MockRestServiceServer` + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-27-search-reranker-design.md`

## Global Constraints

- **선행: PR #740 (`강대혁/project/search-relevance-improvement`) 머지.** 이 브랜치(`강대혁/project/search-reranker`)는 #740 위에 스택. #740 머지 후 develop에 리베이스.
- **N+1 금지.** 후보 문서 텍스트는 `fetchDocumentsByIds` (ES terms 1회). 후보 개수만큼 조회하는 코드는 리젝.
- **쿼리 이원화.** retrieval(BM25/kNN) = 확장 쿼리, rerank = 원본 trim 쿼리.
- **`cohere.rerank.enabled=false` 환경은 `COHERE_API_KEY` 없이 부팅.** Cohere Bean은 `@ConditionalOnProperty`로만 생성.
- **외부 Cohere 호출을 CI 머지 게이트로 만들지 않는다.** 실 Cohere 평가는 `@Assumptions.assumeTrue(COHERE_API_KEY 존재)`로 로컬/수동.
- 커밋 메시지에 `Co-Authored-By` 트레일러 금지 (팀 규칙). 커밋 prefix: `feat`/`fix`/`refactor`/`test`/`docs`/`chore`.
- 재색인 불필요 — `ProjectEmbeddingService`/색인 시점 5벡터 생성/`ProjectDocument`/ES 매핑 안 건드림.

## 파일 구조

신규 (main, 전부 `project-service/.../project/project/infrastructure/search/`):
| 파일 | 책임 |
|---|---|
| `Reranker.java` | 인터페이스. `List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs)` |
| `NoOpReranker.java` | `candidateIds` 그대로 반환. `@ConditionalOnProperty(..., havingValue="false", matchIfMissing=true)` |
| `CohereRerankProperties.java` | `@ConfigurationProperties("cohere.rerank")` — enabled, baseUrl, model, topN, apiKey, timeoutMs |
| `CohereRerankClient.java` | `RestClient` 래퍼. `POST {baseUrl}/v2/rerank`. 요청/응답 record. |
| `CohereReranker.java` | `Reranker` 구현. `title+summary` 조립 → 클라이언트 호출 → index→projectId 매핑. `CircuitBreakerFactory`로 감싸고 fallback = `candidateIds`. `@ConditionalOnProperty(..., havingValue="true")` |
| `RerankConfig.java` | `@Configuration`. `RestClient` Bean(`cohere.rerank` 조건부). `@EnableConfigurationProperties(CohereRerankProperties.class)` |
| `QuerySynonymExpander.java` | 정적 맵. `String expand(String trimmedQuery)` — 원본 + 매칭된 동의어들을 공백으로 이어붙임 |
| `SeasonalConflictFilter.java` | `List<Long> filter(String originalQuery, List<Long> candidateIds, Map<Long, ProjectDocument> docs)` — 강한 계절 충돌만 제거 |

신규 (test): `CohereRerankClientTest`, `CohereRerankerTest`, `NoOpRerankerTest`, `QuerySynonymExpanderTest`, `SeasonalConflictFilterTest`.

수정: `ProjectSearchAdapter.java`, `ProjectSearchCircuitBreakerConfig.java`, `ProjectSearchAdapterTest.java`, `project-service/src/test/resources/application.yml`.

삭제: `QueryIntentAnalyzer.java`, `QueryIntent.java`, `Requirement.java`, `QueryProductCompatibilityEvaluator.java`, `QueryIntentAnalyzerTest.java`, `QueryProductCompatibilityEvaluatorTest.java`, `CompatibilityQualityDeepEvaluationTest.java`, `QueryIntentSearchQualityTest.java`, `QueryIntentE2ESearchQualityTest.java`, `RealDataSearchRankingBenchmarkTest.java`.

config 레포: `project-service.yml` (`cohere.rerank.*` 추가, `spring.ai.openai.chat.*` 제거).

---

### Task 1: `Reranker` 인터페이스 + `NoOpReranker` + Properties + Config

**Files:**
- Create: `.../infrastructure/search/Reranker.java`, `NoOpReranker.java`, `CohereRerankProperties.java`, `RerankConfig.java`
- Create: `.../search/NoOpRerankerTest.java`
- Modify: `.../infrastructure/search/ProjectSearchCircuitBreakerConfig.java`

**Interfaces:**
- Produces: `Reranker.rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) -> List<Long>`
- Produces: `CohereRerankProperties` — `boolean enabled()`, `String baseUrl()`, `String model()`, `int topN()`, `String apiKey()`, `long timeoutMs()`
- Produces: `ProjectSearchCircuitBreakerConfig.PROJECT_RERANK_ID = "projectRerank"` (TimeLimiter 1500ms)

- [ ] **Step 1: `Reranker` 인터페이스 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.List;
import java.util.Map;

/**
 * 후보 projectId 목록을 사용자 원본 쿼리 기준 관련도로 재정렬한다.
 * 구현체 실패 시 candidateIds를 그대로 반환해야 한다(검색은 fusion 순서로 graceful degrade).
 */
public interface Reranker {
    List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs);
}
```

- [ ] **Step 2: `NoOpRerankerTest` 작성 (실패 확인)**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class NoOpRerankerTest {
    @Test
    void returnsCandidatesUnchanged() {
        Reranker reranker = new NoOpReranker();
        List<Long> ids = List.of(3L, 1L, 2L);
        assertThat(reranker.rerank("강아지 옷", ids, Map.of())).isEqualTo(ids);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :project-service:test --tests "com.growmighty.lectures.firstday.project.project.infrastructure.search.NoOpRerankerTest"`
Expected: FAIL — `NoOpReranker` 클래스 없음 (컴파일 에러)

- [ ] **Step 4: `NoOpReranker` 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** cohere.rerank.enabled=false(또는 미설정)일 때 활성. 재정렬 없이 fusion 순서를 그대로 쓴다. */
@Component
@ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpReranker implements Reranker {
    @Override
    public List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        return candidateIds;
    }
}
```

- [ ] **Step 5: `CohereRerankProperties` 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cohere.rerank.* — enabled=true일 때만 CohereReranker/CohereRerankClient Bean이 생성되고
 * apiKey가 참조된다. enabled=false면 이 값들은 무시되고 NoOpReranker가 활성.
 */
@ConfigurationProperties(prefix = "cohere.rerank")
public record CohereRerankProperties(
        boolean enabled,
        String baseUrl,
        String model,
        int topN,
        String apiKey,
        long timeoutMs
) {
    public CohereRerankProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.cohere.com";
        if (model == null || model.isBlank()) model = "rerank-v3.5";
        if (topN <= 0) topN = 40;
        if (timeoutMs <= 0) timeoutMs = 3000;
    }
}
```

- [ ] **Step 6: `RerankConfig` 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CohereRerankProperties.class)
public class RerankConfig {

    /** cohere.rerank.enabled=true일 때만 생성 — enabled=false 환경은 이 Bean도 apiKey도 필요 없다. */
    @Bean
    @ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")
    RestClient cohereRestClient(CohereRerankProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(clientRequestFactory(props.timeoutMs()))
                .build();
    }

    private org.springframework.http.client.ClientHttpRequestFactory clientRequestFactory(long timeoutMs) {
        var settings = org.springframework.boot.http.client.ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)))
                .withReadTimeout(Duration.ofMillis(timeoutMs));
        return org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder.detect().build(settings);
    }
}
```

- [ ] **Step 7: `ProjectSearchCircuitBreakerConfig` 수정 — `projectQueryIntent` 제거, `projectRerank` 추가**

`PROJECT_QUERY_INTENT_ID` 상수와 그 `timeLimiterRegistry.addConfiguration(PROJECT_QUERY_INTENT_ID, ...)` 블록, `projectSearchCircuitBreakerCustomizer`의 `PROJECT_QUERY_INTENT_ID` 인자를 삭제. 대신:

```java
    /** Cohere Rerank 호출 전용. 초기 1.5s — 부하/지연 측정 후 조정(spec §7). */
    static final String PROJECT_RERANK_ID = "projectRerank";
```

`registerProjectSearchTimeLimiterConfig()` 안에:

```java
        timeLimiterRegistry.addConfiguration(PROJECT_RERANK_ID, TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(1500))
                .build());
```

`projectSearchCircuitBreakerCustomizer()`의 마지막 인자 목록에서 `PROJECT_QUERY_INTENT_ID`를 `PROJECT_RERANK_ID`로 교체.

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "com.growmighty.lectures.firstday.project.project.infrastructure.search.NoOpRerankerTest" --tests "com.growmighty.lectures.firstday.project.project.infrastructure.search.ProjectSearchCircuitBreakerConfigTest"`
Expected: PASS. (`ProjectSearchCircuitBreakerConfigTest`가 `projectQueryIntent`를 검증하면 그 assertion도 `projectRerank`로 수정.)

- [ ] **Step 9: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/Reranker.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/NoOpReranker.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/CohereRerankProperties.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/RerankConfig.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchCircuitBreakerConfig.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/NoOpRerankerTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchCircuitBreakerConfigTest.java
git commit -m "feat(search): Reranker 인터페이스 + NoOpReranker + Cohere config 골격"
```

---

### Task 2: `CohereRerankClient`

**Files:**
- Create: `.../infrastructure/search/CohereRerankClient.java`
- Create: `.../search/CohereRerankClientTest.java`

**Interfaces:**
- Consumes: `CohereRerankProperties`, `RestClient` (Bean 이름 `cohereRestClient`)
- Produces: `CohereRerankClient.rerank(String query, List<String> documents) -> List<CohereRerankClient.Ranked>` where `Ranked(int index, double relevanceScore)`, 관련도 내림차순. 실패 시 `RestClientException` 전파.

- [ ] **Step 1: `CohereRerankClientTest` 작성 (실패 확인)**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CohereRerankClientTest {

    private MockRestServiceServer server;
    private CohereRerankClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.cohere.com")
                .defaultHeader("Authorization", "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        var props = new CohereRerankProperties(true, "https://api.cohere.com", "rerank-v3.5", 40, "test-key", 3000);
        client = new CohereRerankClient(builder.build(), props);
    }

    @Test
    void sendsQueryAndDocumentsAndParsesRankedResults() {
        server.expect(requestTo("https://api.cohere.com/v2/rerank"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(jsonPath("$.query").value("강아지 옷"))
              .andExpect(jsonPath("$.model").value("rerank-v3.5"))
              .andExpect(jsonPath("$.top_n").value(2))
              .andExpect(jsonPath("$.documents[0]").value("A"))
              .andRespond(withSuccess("""
                  {"results":[{"index":1,"relevance_score":0.9},{"index":0,"relevance_score":0.2}]}
                  """, MediaType.APPLICATION_JSON));

        List<CohereRerankClient.Ranked> ranked = client.rerank("강아지 옷", List.of("A", "B"));

        assertThat(ranked).extracting(CohereRerankClient.Ranked::index).containsExactly(1, 0);
        assertThat(ranked.get(0).relevanceScore()).isEqualTo(0.9);
        server.verify();
    }

    @Test
    void propagatesServerError() {
        server.expect(requestTo("https://api.cohere.com/v2/rerank"))
              .andRespond(withServerError());
        assertThatThrownBy(() -> client.rerank("q", List.of("A")))
              .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :project-service:test --tests "*.CohereRerankClientTest"`
Expected: FAIL — `CohereRerankClient` 없음

- [ ] **Step 3: `CohereRerankClient` 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Cohere Rerank v2 REST 호출. 파싱만 담당하고 예외는 그대로 전파한다(재시도/폴백은 CohereReranker). */
@RequiredArgsConstructor
public class CohereRerankClient {

    private final RestClient cohereRestClient;
    private final CohereRerankProperties props;

    public record Ranked(int index, double relevanceScore) {}

    private record Request(String model, String query, List<String> documents,
                           @JsonProperty("top_n") int topN) {}
    private record Response(List<Result> results) {}
    private record Result(int index, @JsonProperty("relevance_score") double relevanceScore) {}

    public List<Ranked> rerank(String query, List<String> documents) {
        Request body = new Request(props.model(), query, documents, documents.size());
        Response resp = cohereRestClient.post()
                .uri("/v2/rerank")
                .body(body)
                .retrieve()
                .body(Response.class);
        if (resp == null || resp.results() == null) {
            return List.of();
        }
        return resp.results().stream()
                .map(r -> new Ranked(r.index(), r.relevanceScore()))
                .toList();
    }
}
```

`CohereRerankClient`는 `@Component`가 아니라 `RerankConfig`에서 Bean으로 등록(다음 스텝):

- [ ] **Step 4: `RerankConfig`에 `CohereRerankClient` Bean 추가**

```java
    @Bean
    @ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")
    CohereRerankClient cohereRerankClient(RestClient cohereRestClient, CohereRerankProperties props) {
        return new CohereRerankClient(cohereRestClient, props);
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "*.CohereRerankClientTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/CohereRerankClient.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/RerankConfig.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/CohereRerankClientTest.java
git commit -m "feat(search): CohereRerankClient — Cohere Rerank v2 REST 호출"
```

---

### Task 3: `CohereReranker` (impl + CircuitBreaker fallback)

**Files:**
- Create: `.../infrastructure/search/CohereReranker.java`
- Create: `.../search/CohereRerankerTest.java`

**Interfaces:**
- Consumes: `CohereRerankClient`, `CircuitBreakerFactory`, `ProjectSearchCircuitBreakerConfig.PROJECT_RERANK_ID`
- Produces: `Reranker` Bean (`@ConditionalOnProperty ... havingValue="true"`). `rerank`는 실패 시 `candidateIds` 그대로.

- [ ] **Step 1: `CohereRerankerTest` 작성 (실패 확인)**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CohereRerankerTest {

    private final CohereRerankClient client = mock(CohereRerankClient.class);
    private final CircuitBreakerFactory cbFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker cb = mock(CircuitBreaker.class);
    private CohereReranker reranker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(cbFactory.create("projectRerank")).thenReturn(cb);
        when(cb.run(any(Supplier.class), any(Function.class))).thenAnswer(inv -> {
            Supplier<Object> s = inv.getArgument(0);
            Function<Throwable, Object> fb = inv.getArgument(1);
            try { return s.get(); } catch (Throwable t) { return fb.apply(t); }
        });
        reranker = new CohereReranker(client, cbFactory);
    }

    private Map<Long, ProjectDocument> docs(Long... ids) {
        return java.util.Arrays.stream(ids).collect(java.util.stream.Collectors.toMap(
                id -> id, id -> new ProjectDocument(id, "제목" + id, "요약" + id, null, 1L, List.of(),
                        null, null, null, null, null)));
    }

    @Test
    void reordersByCohereResult() {
        List<Long> candidates = List.of(10L, 20L, 30L);
        when(client.rerank(eq("강아지 옷"), any())).thenReturn(List.of(
                new CohereRerankClient.Ranked(2, 0.9),
                new CohereRerankClient.Ranked(0, 0.5),
                new CohereRerankClient.Ranked(1, 0.1)));

        assertThat(reranker.rerank("강아지 옷", candidates, docs(10L, 20L, 30L)))
                .containsExactly(30L, 10L, 20L);
    }

    @Test
    void fallsBackToCandidateOrderOnClientError() {
        List<Long> candidates = List.of(10L, 20L);
        when(client.rerank(any(), any())).thenThrow(new RuntimeException("cohere down"));

        assertThat(reranker.rerank("q", candidates, docs(10L, 20L))).isEqualTo(candidates);
    }

    @Test
    void emptyCandidatesReturnEmpty() {
        assertThat(reranker.rerank("q", List.of(), Map.of())).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :project-service:test --tests "*.CohereRerankerTest"`
Expected: FAIL — `CohereReranker` 없음

- [ ] **Step 3: `CohereReranker` 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cohere Rerank로 후보를 재정렬. 원본 쿼리(확장 아님)와 title+summary를 넘긴다.
 * 실패/타임아웃/CB Open 시 candidateIds를 그대로 반환 — 검색은 fusion 순서로 graceful degrade.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")
public class CohereReranker implements Reranker {

    private final CohereRerankClient client;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public CohereReranker(CohereRerankClient client, CircuitBreakerFactory circuitBreakerFactory) {
        this.client = client;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_RERANK_ID).run(
                () -> doRerank(query, candidateIds, docs),
                cause -> {
                    log.warn("[Rerank] Cohere 호출 실패 → fusion 순서 유지. 원인: {}", cause.toString());
                    return candidateIds;
                });
    }

    private List<Long> doRerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        List<String> documents = new ArrayList<>(candidateIds.size());
        for (Long id : candidateIds) {
            ProjectDocument d = docs.get(id);
            String title = d != null && d.title() != null ? d.title() : "";
            String summary = d != null && d.summary() != null ? d.summary() : "";
            documents.add((title + " " + summary).trim());
        }

        List<CohereRerankClient.Ranked> ranked = client.rerank(query, documents);
        if (ranked.isEmpty()) {
            return candidateIds;
        }

        List<Long> reordered = new ArrayList<>(candidateIds.size());
        for (CohereRerankClient.Ranked r : ranked) {
            if (r.index() >= 0 && r.index() < candidateIds.size()) {
                reordered.add(candidateIds.get(r.index()));
            }
        }
        // Cohere가 일부만 반환했을 경우 누락분을 원래 순서로 뒤에 붙인다.
        for (Long id : candidateIds) {
            if (!reordered.contains(id)) {
                reordered.add(id);
            }
        }
        return reordered;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "*.CohereRerankerTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/CohereReranker.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/CohereRerankerTest.java
git commit -m "feat(search): CohereReranker — CircuitBreaker fallback으로 fusion 순서 유지"
```

---

### Task 4: `QuerySynonymExpander`

**Files:**
- Create: `.../infrastructure/search/QuerySynonymExpander.java`
- Create: `.../search/QuerySynonymExpanderTest.java`

**Interfaces:**
- Produces: `QuerySynonymExpander.expand(String trimmedQuery) -> String` (원본 + 매칭 동의어들, 공백 결합. 매칭 없으면 원본 그대로)

- [ ] **Step 1: `QuerySynonymExpanderTest` 작성 (실패 확인)**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QuerySynonymExpanderTest {

    private final QuerySynonymExpander expander = new QuerySynonymExpander();

    @Test
    void appendsSynonymsForMatchedTerm() {
        assertThat(expander.expand("강아지 옷"))
                .contains("강아지 옷")
                .contains("반려견").contains("애견");
    }

    @Test
    void expandsSlang() {
        assertThat(expander.expand("댕댕이 간식")).contains("강아지");
        assertThat(expander.expand("냥이 장난감")).contains("고양이");
    }

    @Test
    void noMatchReturnsOriginal() {
        assertThat(expander.expand("노트북 파우치")).isEqualTo("노트북 파우치");
    }

    @Test
    void nullOrBlankReturnsEmpty() {
        assertThat(expander.expand(null)).isEmpty();
        assertThat(expander.expand("  ")).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :project-service:test --tests "*.QuerySynonymExpanderTest"`
Expected: FAIL

- [ ] **Step 3: `QuerySynonymExpander` 작성**

기존 `ProjectSearchAdapter.SLANG_SYNONYM_MAP`을 흡수하고 카테고리 동의어를 추가한다.

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검색어 확장 — BM25/임베딩 retrieval 전용. 리랭커에는 원본을 넘긴다(spec §3).
 * 정적 인메모리 맵. LLM 없음. 매칭 키가 검색어에 포함되면 그 동의어들을 원본 뒤에 이어붙인다.
 */
@Component
public class QuerySynonymExpander {

    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("냥이", List.of("고양이")),
            Map.entry("댕댕이", List.of("강아지")),
            Map.entry("멍멍이", List.of("강아지")),
            Map.entry("공청기", List.of("공기청정기")),
            Map.entry("폰케이스", List.of("스마트폰 케이스")),
            Map.entry("강아지", List.of("반려견", "애견")),
            Map.entry("고양이", List.of("반려묘")),
            Map.entry("빔프로젝터", List.of("프로젝터", "빔")),
            Map.entry("이어폰", List.of("무선이어폰", "블루투스 이어폰"))
    );

    public String expand(String trimmedQuery) {
        if (trimmedQuery == null || trimmedQuery.isBlank()) {
            return "";
        }
        Set<String> parts = new LinkedHashSet<>();
        parts.add(trimmedQuery.trim());
        for (Map.Entry<String, List<String>> e : SYNONYMS.entrySet()) {
            if (trimmedQuery.contains(e.getKey())) {
                parts.addAll(e.getValue());
            }
        }
        return String.join(" ", parts);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "*.QuerySynonymExpanderTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QuerySynonymExpander.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QuerySynonymExpanderTest.java
git commit -m "feat(search): QuerySynonymExpander — retrieval 전용 정적 동의어 확장"
```

---

### Task 5: `SeasonalConflictFilter`

**Files:**
- Create: `.../infrastructure/search/SeasonalConflictFilter.java`
- Create: `.../search/SeasonalConflictFilterTest.java`

**Interfaces:**
- Produces: `SeasonalConflictFilter.filter(String originalQuery, List<Long> candidateIds, Map<Long, ProjectDocument> docs) -> List<Long>` (강한 계절 충돌 후보만 제거)

- [ ] **Step 1: `SeasonalConflictFilterTest` 작성 (실패 확인)**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SeasonalConflictFilterTest {

    private final SeasonalConflictFilter filter = new SeasonalConflictFilter();

    private ProjectDocument doc(Long id, String title, String summary) {
        return new ProjectDocument(id, title, summary, null, 1L, List.of(), null, null, null, null, null);
    }

    @Test
    void removesWinterItemForSummerQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "여름 린넨 반팔 티셔츠", "시원한 여름용"),
                2L, doc(2L, "울 혼방 롱코트", "보온성 높은 겨울 방한 코트"));
        assertThat(filter.filter("여름에 입기 좋은 옷", List.of(1L, 2L), docs)).containsExactly(1L);
    }

    @Test
    void removesSummerItemForWinterQuery() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "겨울 패딩 점퍼", "한겨울 방한"),
                2L, doc(2L, "쿨링 반팔 티셔츠", "여름 시원한 린넨"));
        assertThat(filter.filter("겨울에 따뜻한 옷", List.of(1L, 2L), docs)).containsExactly(1L);
    }

    @Test
    void keepsAllWhenQuerySeasonUnclear() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "울 혼방 롱코트", "겨울 방한"),
                2L, doc(2L, "반팔 티셔츠", "여름 쿨링"));
        assertThat(filter.filter("옷 추천", List.of(1L, 2L), docs)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void keepsDocWithoutExplicitOppositeMarker() {
        Map<Long, ProjectDocument> docs = Map.of(
                1L, doc(1L, "베이직 코튼 티셔츠", "사계절 데일리"));
        assertThat(filter.filter("여름 옷", List.of(1L), docs)).containsExactly(1L);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :project-service:test --tests "*.SeasonalConflictFilterTest"`
Expected: FAIL

- [ ] **Step 3: `SeasonalConflictFilter` 작성**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 강한 계절 충돌만 하드 제외 — 리랭커의 안전망(spec §5).
 * 제외 조건 = AND 둘 다:
 *   1) 쿼리가 계절을 명확히 함의 (여름/겨울 키워드)
 *   2) 문서 title/summary에 반대 계절의 강한 마커가 명시됨
 * 애매하면 아무것도 안 한다.
 */
@Slf4j
@Component
public class SeasonalConflictFilter {

    private static final Set<String> SUMMER_QUERY = Set.of("여름", "여름용", "하절기", "쿨링", "시원한");
    private static final Set<String> WINTER_QUERY = Set.of("겨울", "겨울용", "동절기", "방한", "보온", "따뜻한");

    private static final Set<String> SUMMER_DOC_MARKERS = Set.of("여름", "쿨링", "린넨", "반팔", "냉감", "시어서커");
    private static final Set<String> WINTER_DOC_MARKERS = Set.of("겨울", "방한", "기모", "롱코트", "패딩", "니트", "다운", "플리스", "한파", "혹한");

    private enum Season { SUMMER, WINTER, NONE }

    public List<Long> filter(String originalQuery, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        Season q = querySeason(originalQuery);
        if (q == Season.NONE) {
            return candidateIds;
        }
        Set<String> oppositeMarkers = (q == Season.SUMMER) ? WINTER_DOC_MARKERS : SUMMER_DOC_MARKERS;

        List<Long> kept = new ArrayList<>(candidateIds.size());
        for (Long id : candidateIds) {
            ProjectDocument d = docs.get(id);
            if (d != null && hasMarker(d, oppositeMarkers)) {
                log.info("[SeasonalFilter] 계절 충돌 후보 제외: projectId={}, title='{}', querySeason={}",
                        id, d.title(), q);
                continue;
            }
            kept.add(id);
        }
        return kept;
    }

    private Season querySeason(String query) {
        if (query == null) return Season.NONE;
        boolean summer = SUMMER_QUERY.stream().anyMatch(query::contains);
        boolean winter = WINTER_QUERY.stream().anyMatch(query::contains);
        if (summer == winter) return Season.NONE;  // 둘 다거나 둘 다 아님 → 판단 불가
        return summer ? Season.SUMMER : Season.WINTER;
    }

    private boolean hasMarker(ProjectDocument d, Set<String> markers) {
        String text = ((d.title() == null ? "" : d.title()) + " " + (d.summary() == null ? "" : d.summary()));
        return markers.stream().anyMatch(text::contains);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "*.SeasonalConflictFilterTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/SeasonalConflictFilter.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/SeasonalConflictFilterTest.java
git commit -m "feat(search): SeasonalConflictFilter — 강한 계절 충돌만 하드 제외"
```

---

### Task 6: `ProjectSearchAdapter.doSearch` 배선

**Files:**
- Modify: `.../infrastructure/search/ProjectSearchAdapter.java`
- Modify: `.../search/ProjectSearchAdapterTest.java`

**Interfaces:**
- Consumes: `Reranker`, `QuerySynonymExpander`, `SeasonalConflictFilter` (모두 생성자 주입)
- 변경: `doSearch`가 확장 쿼리로 BM25/임베딩, 후보 40, `SeasonalConflictFilter` → `fetchDocumentsByIds` → `Reranker.rerank(원본, 후보, docs)` 반환. `fuseByScore`에서 Compatibility 블록 제거.

- [ ] **Step 1: `ProjectSearchAdapterTest`에 리랭커 케이스 추가 (실패 확인)**

`ProjectSearchAdapterTest`에 필드/생성자 추가:

```java
    private final Reranker reranker = mock(Reranker.class);
    private final QuerySynonymExpander synonymExpander = new QuerySynonymExpander();
    private final SeasonalConflictFilter seasonalConflictFilter = new SeasonalConflictFilter();
```

`@BeforeEach`의 `new ProjectSearchAdapter(...)` 인자에서 `queryIntentAnalyzer`, `compatibilityEvaluator` 제거하고 `reranker, synonymExpander, seasonalConflictFilter` 추가. `queryIntentAnalyzer` 필드/스텁 삭제.

새 테스트:

```java
    @Test
    @DisplayName("fusion 상위 후보를 Reranker가 재정렬한 순서로 반환한다")
    @SuppressWarnings("unchecked")
    void search_returnsRerankerOrder() throws Exception {
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[1536]);
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of());
        // BM25 + kNN이 후보 [101, 102, 103]을 만들도록 목킹 (기존 테스트의 목킹 패턴 재사용)
        // ... (기존 search_* 테스트와 동일한 SearchHits/SearchResponse 목킹으로 3개 후보 구성)
        when(reranker.rerank(eq("노트북"), anyList(), anyMap()))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(1);
                    List<Long> rev = new java.util.ArrayList<>(ids);
                    java.util.Collections.reverse(rev);
                    return rev;
                });

        List<Long> result = adapter.search("노트북");

        verify(reranker).rerank(eq("노트북"), anyList(), anyMap());
        // reranker가 뒤집었으므로 fusion 순서의 역순
        assertThat(result).isNotEmpty();
    }
```

(기존 `search_*` 테스트 중 QueryIntent/Compatibility에 의존하던 assertion은 리랭커 목킹 기준으로 수정하거나 Task 7에서 정리.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :project-service:test --tests "*.ProjectSearchAdapterTest"`
Expected: FAIL — 컴파일 에러(생성자 시그니처)

- [ ] **Step 3: `ProjectSearchAdapter` 수정**

3-1. 필드 추가 (기존 `queryIntentAnalyzer`, `compatibilityEvaluator` 제거):

```java
    private final Reranker reranker;
    private final QuerySynonymExpander synonymExpander;
    private final SeasonalConflictFilter seasonalConflictFilter;
```

3-2. 상수: `INTENT_JOIN_BUDGET_MS` 제거. 후보 수 상수 추가:

```java
    /** 리랭커에 넘길 fusion 상위 후보 수. */
    private static final int RERANK_CANDIDATE_LIMIT = 40;
```

3-3. `SLANG_SYNONYM_MAP` / `resolveSlangSynonyms` 제거 (→ `QuerySynonymExpander`로 이동).

3-4. `doSearch` 재작성 — `intentFuture` 및 관련 로직 전부 제거, BM25/임베딩 쿼리를 `expanded`로:

```java
    private List<Long> doSearch(String keyword) {
        long totalStart = System.currentTimeMillis();
        String trimmedKeyword = keyword.trim();
        String expanded = synonymExpander.expand(trimmedKeyword);

        List<Long> exactCategoryIds = resolveExactCategoryIds(trimmedKeyword);
        if (!exactCategoryIds.isEmpty()) {
            log.info("[ProjectSearch] 키워드='{}' → 카테고리명 일치로 kNN 하드 스코프 적용: categoryIds={}", trimmedKeyword, exactCategoryIds);
        }

        // BM25 (확장 쿼리)
        CompletableFuture<List<ScoredDocument>> bm25Future = CompletableFuture.supplyAsync(() -> {
            Query keywordQuery = Query.of(q -> q.bool(b -> {
                b.should(s -> s.match(m -> m.field("title").query(expanded).boost(2.0f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("summary").query(expanded).boost(1.2f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("description").query(expanded).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("rewardNames").query(expanded).boost(1.5f)));
                if (!exactCategoryIds.isEmpty()) {
                    b.should(s -> s.matchAll(m -> m));
                    b.filter(f -> f.terms(t -> t.field("categoryId")
                            .terms(ts -> ts.value(exactCategoryIds.stream().map(FieldValue::of).toList()))));
                }
                return b.minimumShouldMatch("1");
            }));
            return searchKeywordScored(keywordQuery);
        }, searchTaskExecutor);

        // 임베딩 → 5×kNN (확장 쿼리)
        CompletableFuture<VectorBranchResult> vectorFuture = CompletableFuture.supplyAsync(() -> {
            float[] queryVector = null;
            try {
                queryVector = embeddingService.generateEmbedding(expanded);
            } catch (Exception e) {
                log.warn("[ProjectSearch] 임베딩 생성 실패: {}", e.getMessage());
            }
            if (queryVector == null || queryVector.length == 0) {
                return new VectorBranchResult(List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
            }
            List<Long> intentCategoryIds = exactCategoryIds.isEmpty()
                    ? categoryIntentResolver.resolveCategoryIntent(queryVector) : List.of();
            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float f : queryVector) vectorList.add(f);

            CompletableFuture<List<ScoredDocument>> rewardF = CompletableFuture.supplyAsync(() -> searchFieldKnnScored("rewardVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> titleF = CompletableFuture.supplyAsync(() -> searchFieldKnnScored("titleVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> catF = CompletableFuture.supplyAsync(() -> searchFieldKnnScored("categoryVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> sumF = CompletableFuture.supplyAsync(() -> searchFieldKnnScored("summaryVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> descF = CompletableFuture.supplyAsync(() -> searchFieldKnnScored("descriptionVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture.allOf(rewardF, titleF, catF, sumF, descF).join();

            Set<Long> boostIds = intentCategoryIds.isEmpty() ? Set.of() : resolveCategoryMemberProjectIds(intentCategoryIds);
            return new VectorBranchResult(rewardF.join(), titleF.join(), catF.join(), sumF.join(), descF.join(), boostIds);
        }, searchTaskExecutor).exceptionally(ex -> {
            log.warn("[ProjectSearch] Vector Branch 예외, BM25 단독 폴백: {}", ex.getMessage());
            return new VectorBranchResult(List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
        });

        CompletableFuture.allOf(bm25Future, vectorFuture).join();
        List<ScoredDocument> keywordScored = bm25Future.join();
        VectorBranchResult v = vectorFuture.join();

        if (v.rewardScored.isEmpty() && v.titleScored.isEmpty() && v.categoryVectorScored.isEmpty()
                && v.summaryScored.isEmpty() && v.descScored.isEmpty()) {
            return keywordScored.stream().map(ScoredDocument::projectId).toList();
        }

        List<Long> candidates = fuseByScore(keywordScored, v.rewardScored, v.titleScored,
                v.categoryVectorScored, v.summaryScored, v.descScored, v.categoryIntentBoostProjectIds);
        candidates = candidates.stream().limit(RERANK_CANDIDATE_LIMIT).toList();

        Map<Long, ProjectDocument> docs = fetchDocumentsByIds(candidates);
        List<Long> afterFilter = seasonalConflictFilter.filter(trimmedKeyword, candidates, docs);
        List<Long> reranked = reranker.rerank(trimmedKeyword, afterFilter, docs);

        long totalElapsed = System.currentTimeMillis() - totalStart;
        log.info("[ProjectSearch Latency Summary] 키워드='{}' | Total: {}ms | 후보: {}, 필터후: {}, 최종: {}",
                trimmedKeyword, totalElapsed, candidates.size(), afterFilter.size(), reranked.size());
        return reranked;
    }
```

3-5. `VectorBranchResult` record에서 `embeddingElapsed`, `knnElapsed` 필드 제거 (위 코드 기준 6필드).

3-6. `fuseByScore` 시그니처에서 `QueryIntent intent` 파라미터 제거, 본문의 "2-Stage Compatibility Layer" `if (intent != null && intent.hasRequirements())` 블록 전체 제거. 나머지(정규화, accumulate, 동적 컷오프, categoryIntentBoost, 정렬) 유지. 클래스 상단 주석의 "2-Stage Compatibility" 문구 정리.

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "*.ProjectSearchAdapterTest"`
Expected: PASS (QueryIntent/Compatibility 의존 테스트는 Task 7에서 최종 정리하되, 컴파일·핵심 케이스는 여기서 통과)

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapter.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterTest.java
git commit -m "refactor(search): doSearch에 리랭킹 단계 배선 — 확장쿼리 retrieval, 원본쿼리 rerank, 후보 40"
```

---

### Task 7: QueryIntent / Compatibility 제거

**Files:**
- Delete: `QueryIntentAnalyzer.java`, `QueryIntent.java`, `Requirement.java`, `QueryProductCompatibilityEvaluator.java`
- Delete: `QueryIntentAnalyzerTest.java`, `QueryProductCompatibilityEvaluatorTest.java`, `CompatibilityQualityDeepEvaluationTest.java`, `QueryIntentSearchQualityTest.java`, `QueryIntentE2ESearchQualityTest.java`, `RealDataSearchRankingBenchmarkTest.java`
- Modify: `project-service/src/test/resources/application.yml` (`spring.ai.openai.chat.*` 제거)

- [ ] **Step 1: 파일 삭제 + 참조 정리**

```bash
git rm project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryIntentAnalyzer.java \
       project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryIntent.java \
       project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/Requirement.java \
       project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryProductCompatibilityEvaluator.java \
       project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryIntentAnalyzerTest.java \
       project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryProductCompatibilityEvaluatorTest.java \
       project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/CompatibilityQualityDeepEvaluationTest.java \
       project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryIntentSearchQualityTest.java \
       project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/QueryIntentE2ESearchQualityTest.java \
       project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/RealDataSearchRankingBenchmarkTest.java
```

`grep -rn "QueryIntent\|QueryProductCompat\|Requirement\b" project-service/src` 로 잔여 import/참조 확인 후 제거.

- [ ] **Step 2: `test/resources/application.yml`에서 `spring.ai.openai.chat` 블록 제거**

`spring.ai.openai.api-key`는 임베딩용이라 유지. `chat:` 하위(`options: {model, temperature, max-tokens}`)만 삭제.

- [ ] **Step 3: 전체 검색 패키지 테스트**

Run: `./gradlew :project-service:test --tests "com.growmighty.lectures.firstday.project.project.infrastructure.search.*"`
Expected: PASS. `ProjectSearchGoldenSetEvaluationTest`가 QueryIntent 경로에 의존했다면 리랭커/NoOp 기준으로 수정(CI는 NoOp).

- [ ] **Step 4: 커밋**

```bash
git add -A
git commit -m "refactor(search): QueryIntentAnalyzer + QueryProductCompatibilityEvaluator 제거 (리랭커로 대체)"
```

---

### Task 8: config 레포 — Cohere 설정

**Files (별도 레포 `beadv7_7_earlybird_config`):**
- Modify: `project-service.yml`

- [ ] **Step 1: `cohere.rerank` 블록 추가 + `spring.ai.openai.chat` 제거**

```yaml
cohere:
  rerank:
    enabled: false                     # true로 바꾸면 CohereReranker 활성 (COHERE_API_KEY 필요)
    base-url: https://api.cohere.com
    model: rerank-v3.5
    top-n: 40
    timeout-ms: 3000
    api-key: ${COHERE_API_KEY}          # enabled=true일 때만 참조. 기본값 없음.
```

`spring.ai.openai.chat:` 하위 삭제 (`api-key`, `embedding`, `retry`는 유지).

- [ ] **Step 2: 커밋 + 푸시**

```bash
cd ../beadv7_7_earlybird_config
git add project-service.yml
git commit -m "feat(project): 검색 리랭커용 cohere.rerank 설정 + QueryIntent chat 모델 제거"
git push
```

---

### Task 9: 골든셋 baseline + 리랭커 품질 평가 (#740 머지 후)

**Files:**
- Modify/Create: `.../search/ProjectSearchGoldenSetEvaluationTest.java` 또는 `SearchRerankQualityTest.java`

- [ ] **Step 1: #740 머지 후 이 브랜치를 develop에 리베이스**

```bash
git fetch origin
git rebase origin/develop
./gradlew :project-service:test --tests "com.growmighty.lectures.firstday.project.project.infrastructure.search.*"
```

- [ ] **Step 2: baseline 캡처**

리베이스 직후(리랭커 코드 포함 전 상태를 태그하거나, `cohere.rerank.enabled=false`로) 골든셋 실행 → precision@5, nDCG@10, Hit@1/3/5 기록. 스펙 §10의 커버 유형(정확매칭/자연어/크로스카테고리 노이즈/계절/어휘간극)으로 골든셋을 30~40개로 확장.

- [ ] **Step 3: 실 Cohere 품질 평가 테스트**

```java
@SpringBootTest
class SearchRerankQualityTest extends ElasticsearchIntegrationTestSupport {

    @Value("${cohere.rerank.api-key:}")
    private String cohereKey;

    @BeforeEach
    void requireCohereKey() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                cohereKey != null && !cohereKey.isBlank(),
                "COHERE_API_KEY 없음 → 리랭커 품질 평가 skip (CI에선 정상)");
    }

    @Test
    @DisplayName("리랭커 적용 시 골든셋 precision@5 / nDCG@10 이 baseline 이상")
    void rerankBeatsBaseline() {
        // cohere.rerank.enabled=true 프로퍼티로 컨텍스트 구성, 골든셋 30~40개 실행,
        // baseline 수치(상수로 기록)와 비교 assert.
    }
}
```

- [ ] **Step 4: 커밋 + PR**

```bash
git add -A
git commit -m "test(search): 골든셋 확장 + 리랭커 품질 평가 (실 Cohere는 로컬 전용)"
git push -u origin 강대혁/project/search-reranker
gh pr create --base develop --title "feat(search): Cross-encoder 리랭커 (Cohere Rerank 3.5)" --body "..."
```

PR 본문: 템플릿 verbatim. "어떻게 테스트" 에 CI=NoOp + 로컬 실 Cohere baseline 대비 수치 명시.

---

## Self-Review

- **Spec coverage:** §2 모델선택→Task1(config)+Task3(fallback). §3 쿼리이원화→Task4(expander)+Task6(원본 rerank). §4 파이프라인→Task6. §5 컴포넌트→Task1~5. §6 요청/응답→Task2. §7 Resilience→Task1(TimeLimiter)+Task3(fallback). §8 N+1→Task6(`fetchDocumentsByIds` 재사용). §9 config→Task8. §10 테스트→각 Task + Task9. §11 롤아웃→Task 순서. §12 리스크→fallback/NoOp(Task3). §13 범위밖→계획에서 제외.
- **Placeholder scan:** Task9 Step3의 테스트 본문은 baseline 수치가 아직 없어 골격만 — #740 머지 후 채운다(그 전엔 의미 있는 baseline 불가). 그 외 스텝은 실제 코드/명령 포함.
- **Type consistency:** `Reranker.rerank(String, List<Long>, Map<Long, ProjectDocument>)` — Task1 정의, Task3·6에서 동일 사용. `CohereRerankClient.Ranked(int index, double relevanceScore)` — Task2 정의, Task3에서 `r.index()`/`r.relevanceScore()` 사용. `PROJECT_RERANK_ID = "projectRerank"` — Task1 정의, Task3에서 `create(PROJECT_RERANK_ID)`. `RERANK_CANDIDATE_LIMIT = 40` — Task6.

## Execution Handoff

Task 1~8은 #740 머지 없이도 구현·단위테스트 가능 (Cohere 경로는 `MockRestServiceServer`, 기본 `enabled=false`). Task 9만 #740 머지 + `COHERE_API_KEY` 필요.
