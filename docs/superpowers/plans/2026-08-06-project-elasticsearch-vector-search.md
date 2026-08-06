# 프로젝트 검색 ES 키워드+벡터 하이브리드 검색 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `project-service`의 `findAll(keyword, ...)` 키워드 검색을, Elasticsearch(nori 키워드 매치) + OpenAI 임베딩(dense_vector kNN) 하이브리드 검색으로 교체한다.

**Architecture:** MySQL을 source of truth로 유지. `keyword`가 있으면 ES에서 nori match ∪ kNN 하이브리드 쿼리로 candidateProjectIds만 얻어오고, categoryId/status/role 가시성 필터링과 정렬은 지금처럼 MySQL `Specification`이 그대로 담당한다. ES 문서에는 title/summary/description/embedding만 있고 categoryId/status는 없다(중복 동기화 안 함). 색인(create/update/delete 훅)은 실패해도 원본 트랜잭션에 영향을 주지 않고 로그만 남기고 삼킨다. 검색 자체가 실패하면 폴백 없이 `ServiceUnavailableException`(503)을 그대로 전파한다(기존 LIKE 검색은 임시 스텁이었으므로 완전히 제거).

**Tech Stack:** Spring Data Elasticsearch(`spring-boot-starter-data-elasticsearch`) + Spring AI OpenAI(`spring-ai-starter-model-openai`, `EmbeddingModel`) + 기존 `CircuitBreakerFactory`(Resilience4j) 패턴.

**설계 문서:** `docs/superpowers/specs/2026-08-06-project-elasticsearch-vector-search-design.md` (이 계획이 구현하는 스펙 원본)

## Global Constraints

- `ProjectService.findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort, UserRole requesterRole)` 시그니처와 `List<ProjectResponse>` 반환 타입은 절대 바꾸지 않는다.
- ES 문서에는 `categoryId`/`status`를 넣지 않는다 — MySQL이 그 필터링을 계속 전담한다.
- 임베딩 모델: OpenAI `text-embedding-3-small`, 1536차원, cosine similarity.
- `keyword` 검색이 ES 장애로 실패하면 그대로 `ServiceUnavailableException`(503) — LIKE 폴백을 다시 만들지 않는다.
- 기존 `buildSpecification`의 `keyword` LIKE 분기(`ProjectServiceImpl.java:358-363`)는 삭제 대상이다.
- 재색인 엔드포인트는 `/internal/v1/projects/reindex` — 게이트웨이 라우트 없는 서비스 간 내부 API(`ProjectInternalController`)에 추가한다.
- OpenAI 임베딩 호출은 프레임워크(Spring AI `EmbeddingModel`)로, ES 연동은 프레임워크(Spring Data Elasticsearch `ElasticsearchOperations`)로 — 직접 HTTP 클라이언트를 새로 작성하지 않는다.
- 각 서비스 모듈의 로컬 `application.yml`은 `spring.application.name`과 config-server import만 갖는다(`CLAUDE.md` 컨벤션) — 실제 OpenAI API 키는 `beadv7_7_earlybird_config`(별도 private 리포, 이 작업 범위 밖)에 추가한다. `src/test/resources/application.yml`은 예외로, 테스트가 config-server 없이 자립 실행되도록 여기서 직접 채운다.
- 라이브러리 정확한 버전/유틸리티 메서드 이름(Spring AI, Spring Data Elasticsearch의 정확한 fluent API)은 Boot 4.1/Spring Cloud 2025.1.2와의 호환성이 이 문서 작성 시점에 검증되지 않았다 — Task 1~2에서 처음 의존성을 받아올 때 실제로 resolve되는 버전의 API를 확인하고, 이 계획의 코드와 다르면 같은 역할을 하는 실제 API로 맞춰 조정한다(TDD 루프의 "실행해서 실패 이유 확인" 단계가 이걸 바로 드러낸다).

---

## 파일 구조

**신규**
- `project-service/src/main/java/.../project/infrastructure/search/ProjectDocument.java` — ES 문서 매핑
- `project-service/src/main/java/.../project/infrastructure/search/ProjectSearchAdapter.java` — `ProjectSearchPort` 구현체 (색인/삭제/검색)
- `project-service/src/main/java/.../project/application/port/ProjectSearchPort.java` — 포트 인터페이스
- `project-service/src/main/resources/elasticsearch/project-index-settings.json` — nori 커스텀 분석기 설정
- `project-service/src/main/resources/elasticsearch/project-index-mapping.json` — 필드 매핑(dense_vector 포함)
- `project-service/src/test/java/.../support/ElasticsearchIntegrationTestSupport.java` — nori 포함 커스텀 ES 이미지를 쓰는 Testcontainers 싱글턴 (기존 `MySqlIntegrationTestSupport`와 같은 패턴)
- `project-service/src/test/java/.../project/infrastructure/search/ProjectSearchIndexBootstrapTest.java` — 인덱스 부트스트랩 + nori 동작 통합 테스트
- `project-service/src/test/java/.../project/infrastructure/search/ProjectSearchAdapterTest.java` — 색인/삭제/검색 유닛 테스트(모킹)
- `project-service/src/test/java/.../project/infrastructure/search/ProjectSearchAdapterIntegrationTest.java` — 색인→검색 통합 테스트(Testcontainers ES, `EmbeddingModel`은 스텁)
- `project-service/src/test/java/.../project/application/ProjectServiceImplSearchIndexingTest.java` — create/update/delete 색인 훅 검증
- `project-service/src/test/java/.../project/application/ProjectServiceImplFindAllSearchTest.java` — `findAll` keyword→ES 라우팅 검증
- `project-service/src/test/java/.../project/presentation/ProjectInternalControllerReindexTest.java` — 재색인 엔드포인트 검증

**변경**
- `project-service/build.gradle` — 의존성 3종 추가
- `project-service/src/test/resources/application.yml` — OpenAI 더미 키 추가
- `project-service/src/main/java/.../project/application/ProjectService.java` — `void reindexAllProjects()` 추가
- `project-service/src/main/java/.../project/application/ProjectServiceImpl.java` — `searchPort` 필드 추가, `create`/`update`/`delete`/`findAll`/`reindexAllProjects` 수정, `buildSpecification`에서 LIKE 분기 제거
- `project-service/src/main/java/.../project/presentation/ProjectInternalController.java` — `POST /internal/v1/projects/reindex` 추가
- 기존 `ProjectServiceImpl` 직접 생성 테스트 5개 (`ProjectServiceImplDeleteTest`, `ProjectServiceImplCancelTest`, `ProjectServiceImplOwnershipTest`, `ProjectServiceImplReconciliationTest`, `ProjectServiceImplRetryTest`) — 생성자 호출에 `searchPort` 인자 추가

---

### Task 1: Elasticsearch 인프라 플러밍 (의존성 + 문서 매핑 + nori 부트스트랩)

**Files:**
- Modify: `project-service/build.gradle`
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectDocument.java`
- Create: `project-service/src/main/resources/elasticsearch/project-index-settings.json`
- Create: `project-service/src/main/resources/elasticsearch/project-index-mapping.json`
- Create: `project-service/src/test/java/com/growmighty/lectures/firstday/project/support/ElasticsearchIntegrationTestSupport.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchIndexBootstrapTest.java`

**Interfaces:**
- Produces: `ProjectDocument(Long projectId, String title, String summary, String description, float[] embedding)` — record. `@Id`는 `projectId`.
- Produces: ES 인덱스가 컨텍스트 기동 시 자동으로 생성된다(존재하지 않을 때만) — 뒤 Task들이 별도로 인덱스 생성을 신경 쓸 필요 없음.

- [ ] **Step 1: `project-service/build.gradle`에 의존성 추가**

```groovy
dependencies {
    // ... 기존 의존성 그대로 ...

    // 프로젝트 키워드 검색(nori) — 프레임워크 기본 통합(ElasticsearchOperations) 사용, 커스텀 HTTP 클라이언트 없음.
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'

    // 실제 nori가 포함된 ES(로컬 infrastructure/elasticsearch/Dockerfile과 동일 이미지)로 색인/검색을 검증.
    testImplementation 'org.testcontainers:elasticsearch'
}

dependencyManagement {
    imports {
        // Spring AI(OpenAI 임베딩)는 프로젝트 전체가 아니라 project-service만 쓰므로 루트가 아니라
        // 여기서 BOM을 받는다. Boot 4.1과 호환되는 최신 GA 버전 — 첫 의존성 resolve 시 실패하면
        // Maven Central에 없는 마일스톤/스냅샷일 수 있으니, 그 경우 루트 build.gradle의
        // `repositories { mavenCentral() }` 옆에 `maven { url 'https://repo.spring.io/milestone' }`를
        // 추가한다.
        mavenBom 'org.springframework.ai:spring-ai-bom:1.0.0'
    }
}
```

(`spring-ai-starter-model-openai` 자체는 Task 2에서 추가한다 — 이 Task는 ES만 다룬다.)

- [ ] **Step 2: nori 인덱스 설정 JSON 작성**

`project-service/src/main/resources/elasticsearch/project-index-settings.json`:
```json
{
  "index": {
    "analysis": {
      "analyzer": {
        "korean": {
          "type": "custom",
          "tokenizer": "nori_tokenizer"
        }
      }
    }
  }
}
```

- [ ] **Step 3: 필드 매핑 JSON 작성 (dense_vector 포함)**

`project-service/src/main/resources/elasticsearch/project-index-mapping.json`:
```json
{
  "properties": {
    "projectId": { "type": "long" },
    "title": { "type": "text", "analyzer": "korean" },
    "summary": { "type": "text", "analyzer": "korean" },
    "description": { "type": "text", "analyzer": "korean" },
    "embedding": {
      "type": "dense_vector",
      "dims": 1536,
      "index": true,
      "similarity": "cosine"
    }
  }
}
```

- [ ] **Step 4: `ProjectDocument` 작성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectDocument.java`:
```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * ES 검색 인덱스 전용 문서. categoryId/status는 일부러 넣지 않는다 — 그 필터링은 MySQL
 * Specification이 candidateProjectIds에 대해 그대로 수행한다(design doc 참고). 필드 매핑은
 * 어노테이션이 아니라 project-index-mapping.json으로 직접 관리한다(dense_vector 설정을
 * 어노테이션 속성 이름에 기대지 않기 위해).
 */
@Document(indexName = "projects")
@Setting(settingPath = "elasticsearch/project-index-settings.json")
@Mapping(mappingPath = "elasticsearch/project-index-mapping.json")
public record ProjectDocument(
        @Id Long projectId,
        String title,
        String summary,
        String description,
        float[] embedding
) {
}
```

- [ ] **Step 5: 인덱스 부트스트랩 (앱 기동 시 없으면 생성)**

`ProjectSearchAdapter`는 Task 2에서 만들지만, 인덱스 생성 자체는 ES 전용 관심사라 이 Task에서 별도 컴포넌트로 분리한다.

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchIndexInitializer.java`:
```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * ES는 커스텀 분석기(nori)/dense_vector 필드를 인덱스 "생성 시점"에만 지정할 수 있다 — 문서를
 * 먼저 저장해 자동 생성되게 두면 우리가 원하는 설정 없이 만들어진다. 그래서 기동 시 인덱스가
 * 없으면 명시적으로 설정+매핑을 갖춰 만든다. ES가 기동 시점에 잠깐 안 떠 있어도 앱 부팅
 * 자체를 막지 않는다 — 이후 색인/검색 호출은 어차피 각자의 실패 처리 경로(로그 흡수 / 503)를 탄다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchIndexInitializer {

    private final ElasticsearchOperations elasticsearchOperations;

    @PostConstruct
    void ensureIndexExists() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(ProjectDocument.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
            }
        } catch (RuntimeException e) {
            log.warn("프로젝트 검색 인덱스 초기화 실패 — 검색 기능이 정상 동작하지 않을 수 있습니다.", e);
        }
    }
}
```

- [ ] **Step 6: Testcontainers ES(nori) 지원 베이스 클래스 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/support/ElasticsearchIntegrationTestSupport.java`:
```java
package com.growmighty.lectures.firstday.project.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Paths;

/**
 * MySqlIntegrationTestSupport와 같은 싱글턴 컨테이너 패턴. 순정 elasticsearch 이미지가 아니라
 * infrastructure/elasticsearch/Dockerfile로 빌드한 nori 포함 이미지를 그대로 써서, 로컬/CI
 * 어디서든 실제 nori 형태소분석기 동작까지 검증한다.
 */
public abstract class ElasticsearchIntegrationTestSupport {

    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            new ImageFromDockerfile("project-service-test-es", false)
                    .withDockerfile(Paths.get("../infrastructure/elasticsearch/Dockerfile")))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");

    static {
        ELASTICSEARCH.start();
    }
}
```

- [ ] **Step 7: 부트스트랩 통합 테스트 작성 (실패 확인용 — 아직 아무 프로덕션 코드도 없는 상태에서 컴파일 여부만 우선 확인)**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchIndexBootstrapTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeQuery;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProjectSearchIndexBootstrapTest extends ElasticsearchIntegrationTestSupport {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private static float[] dummyVector() {
        float[] vector = new float[1536];
        vector[0] = 1.0f;
        return vector;
    }

    @Test
    @DisplayName("기동 시 인덱스가 nori 분석기 설정으로 생성되어, 형태소가 분리된 한국어 검색이 매치된다")
    void indexBootstrapsWithNoriAnalyzer() {
        elasticsearchOperations.save(new ProjectDocument(1L, "한국어 형태소 분석기 테스트", null, null, dummyVector()));
        elasticsearchOperations.save(new ProjectDocument(2L, "전혀 관련 없는 다른 프로젝트 제목", null, null, dummyVector()));

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m.field("title").query("분석기")))
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(query, ProjectDocument.class);

        assertThat(hits.getSearchHits()).hasSize(1);
        assertThat(hits.getSearchHits().get(0).getContent().projectId()).isEqualTo(1L);
    }
}
```

- [ ] **Step 8: 실행해서 통과하는지 확인**

Run: `./gradlew :project-service:test --tests "ProjectSearchIndexBootstrapTest" -i`
Expected: PASS. (첫 실행은 Testcontainers가 `infrastructure/elasticsearch/Dockerfile`로 이미지를 빌드하느라 느릴 수 있다 — nori 플러그인 설치 때문. 이후 실행은 Docker 레이어 캐시로 빨라진다.) 실패하면 `NativeQuery`/`Query` 관련 임포트 경로나 메서드 이름이 실제 resolve된 Spring Data Elasticsearch 버전과 다른지 우선 확인(Global Constraints 참고).

- [ ] **Step 9: 전체 빌드가 깨지지 않는지 확인**

Run: `./gradlew :project-service:compileJava :project-service:compileTestJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: 커밋**

```bash
git add project-service/build.gradle \
        project-service/src/main/resources/elasticsearch \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/support/ElasticsearchIntegrationTestSupport.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchIndexBootstrapTest.java
git commit -m "$(cat <<'EOF'
Feat: 프로젝트 검색 ES 인덱스(nori) 부트스트랩 추가

ProjectDocument 매핑 + nori 커스텀 분석기 설정을 기동 시 자동으로
생성한다. Testcontainers로 실제 nori 포함 ES 이미지를 띄워 형태소
분리 검색이 동작하는지 통합 테스트로 검증.
EOF
)"
```

---

### Task 2: `ProjectSearchPort` / `ProjectSearchAdapter` (임베딩 + 색인/삭제/검색)

**Files:**
- Modify: `project-service/build.gradle`
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSearchPort.java`
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapter.java`
- Modify: `project-service/src/test/resources/application.yml`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterTest.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterIntegrationTest.java`

**Interfaces:**
- Consumes: `ProjectDocument`(Task 1), `ElasticsearchIntegrationTestSupport`(Task 1)
- Produces: `ProjectSearchPort { void index(Project project); void remove(Long projectId); List<Long> search(String keyword); }` — Task 4가 이 인터페이스로 `ProjectServiceImpl`에 주입한다.

- [ ] **Step 1: Spring AI OpenAI 의존성 추가**

`project-service/build.gradle`의 `dependencies` 블록에 추가:
```groovy
    // OpenAI 임베딩 — 커스텀 HTTP 클라이언트 대신 Spring AI의 EmbeddingModel 추상화를 쓴다.
    implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

- [ ] **Step 2: 테스트 프로필에 더미 OpenAI 키 추가 (컨텍스트 기동만 되면 됨, 실제 호출은 테스트에서 모킹)**

`project-service/src/test/resources/application.yml`의 `spring:` 블록에 추가:
```yaml
  ai:
    openai:
      api-key: test-key-not-a-real-key
```

- [ ] **Step 3: `ProjectSearchPort` 인터페이스 작성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSearchPort.java`:
```java
package com.growmighty.lectures.firstday.project.project.application.port;

import com.growmighty.lectures.firstday.project.project.domain.Project;

import java.util.List;

/**
 * ES 검색 인덱스에 대한 계약. index/remove는 실패해도 예외를 던지지 않는다(호출부의 MySQL
 * 트랜잭션·응답에 영향을 주면 안 되므로 — 구현체가 내부에서 흡수). search만 ES 장애 시
 * ServiceUnavailableException을 던진다(design doc: LIKE 폴백 없음, 명시적 503).
 */
public interface ProjectSearchPort {

    /** 색인 실패는 로그만 남기고 삼킨다 — 절대 예외를 던지지 않는다. */
    void index(Project project);

    /** 삭제 실패는 로그만 남기고 삼킨다 — 절대 예외를 던지지 않는다. */
    void remove(Long projectId);

    /** nori 키워드 매치 ∪ 임베딩 kNN 하이브리드 검색. 매치 없으면 빈 리스트. ES 장애 시 ServiceUnavailableException. */
    List<Long> search(String keyword);
}
```

- [ ] **Step 4: 실패하는 유닛 테스트 먼저 작성 (`ProjectSearchAdapter`가 아직 없음)**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderHttpClientTest와 같은 방식으로 CircuitBreaker.run(...)을 "그대로 실행"하도록 스텁한다.
 * index/remove는 CircuitBreaker를 안 쓰고 자체 try/catch로 흡수하므로 이 스텁은 search()에만 쓰인다.
 */
class ProjectSearchAdapterTest {

    private final ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private ProjectSearchAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(circuitBreakerFactory.create("projectSearch")).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<Object> toRun = invocation.getArgument(0);
            Function<Throwable, Object> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        });
        adapter = new ProjectSearchAdapter(elasticsearchOperations, embeddingModel, circuitBreakerFactory);
    }

    private Project project() {
        return Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("색인이 성공하면 임베딩을 생성해 ES에 저장한다")
    void index_success_savesDocument() {
        when(embeddingModel.embed("title summary desc")).thenReturn(new float[1536]);

        adapter.index(project());

        verify(elasticsearchOperations).save(any(ProjectDocument.class));
    }

    @Test
    @DisplayName("임베딩 생성이나 ES 저장이 실패해도 예외를 던지지 않고 삼킨다")
    void index_failure_doesNotThrow() {
        when(embeddingModel.embed(any(String.class))).thenThrow(new RuntimeException("openai down"));

        assertThatCode(() -> adapter.index(project())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("삭제가 성공하면 ES에서 문서를 지운다")
    void remove_success_deletesDocument() {
        adapter.remove(1L);

        verify(elasticsearchOperations).delete("1", ProjectDocument.class);
    }

    @Test
    @DisplayName("삭제가 실패해도 예외를 던지지 않고 삼킨다")
    void remove_failure_doesNotThrow() {
        when(elasticsearchOperations.delete("1", ProjectDocument.class)).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> adapter.remove(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("검색이 성공하면 매치된 문서들의 projectId를 반환한다")
    @SuppressWarnings("unchecked")
    void search_success_returnsProjectIds() {
        when(embeddingModel.embed("keyword")).thenReturn(new float[1536]);
        SearchHits<ProjectDocument> hits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(new ProjectDocument(42L, "title", null, null, new float[1536]));
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(elasticsearchOperations.search(any(), eq(ProjectDocument.class))).thenReturn(hits);

        List<Long> result = adapter.search("keyword");

        assertThat(result).containsExactly(42L);
    }

    @Test
    @DisplayName("ES 검색 호출이 실패하면 조용히 넘기지 않고 503으로 변환한다 (LIKE 폴백 없음)")
    void search_failure_throwsServiceUnavailable() {
        when(embeddingModel.embed(any(String.class))).thenThrow(new RuntimeException("openai down"));

        assertThatThrownBy(() -> adapter.search("keyword"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
```

- [ ] **Step 5: 테스트 실행 → 컴파일 실패 확인 (ProjectSearchAdapter 없음)**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `cannot find symbol: class ProjectSearchAdapter`

- [ ] **Step 6: `ProjectSearchAdapter` 구현**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapter.java`:
```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private static final int KNN_K = 20;
    private static final int KNN_NUM_CANDIDATES = 100;
    // NativeQuery 기본 페이지 크기(10)에 걸리면 매치가 10개보다 많을 때 조용히 잘린다 —
    // MySQL 쪽 최종 필터링/정렬을 신뢰하는 후보 집합이라 넉넉하게 잡는다.
    private static final int MAX_RESULTS = 200;

    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingModel embeddingModel;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public void index(Project project) {
        try {
            String text = String.join(" ",
                    project.getTitle(),
                    project.getSummary() != null ? project.getSummary() : "",
                    project.getDescription() != null ? project.getDescription() : "");
            float[] embedding = embeddingModel.embed(text);
            elasticsearchOperations.save(new ProjectDocument(
                    project.getProjectId(), project.getTitle(), project.getSummary(),
                    project.getDescription(), embedding));
        } catch (RuntimeException e) {
            log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), e);
        }
    }

    @Override
    public void remove(Long projectId) {
        try {
            elasticsearchOperations.delete(String.valueOf(projectId), ProjectDocument.class);
        } catch (RuntimeException e) {
            log.warn("프로젝트 검색 색인 삭제 실패. projectId={}", projectId, e);
        }
    }

    @Override
    public List<Long> search(String keyword) {
        return circuitBreakerFactory.create("projectSearch").run(
                () -> doSearch(keyword),
                this::searchFallback);
    }

    private List<Long> doSearch(String keyword) {
        List<Float> queryVector = toFloatList(embeddingModel.embed(keyword));
        Query query = Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m.field("title").query(keyword)))
                .should(s -> s.match(m -> m.field("summary").query(keyword)))
                .should(s -> s.match(m -> m.field("description").query(keyword)))
                .should(s -> s.knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(KNN_K)
                        .numCandidates(KNN_NUM_CANDIDATES)))));
        NativeQuery nativeQuery = NativeQuery.builder().withQuery(query).withMaxResults(MAX_RESULTS).build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream().map(hit -> hit.getContent().projectId()).toList();
    }

    private List<Long> searchFallback(Throwable cause) {
        log.warn("프로젝트 검색 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }

    private List<Float> toFloatList(float[] values) {
        List<Float> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(value);
        }
        return list;
    }
}
```

- [ ] **Step 7: 유닛 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectSearchAdapterTest"`
Expected: PASS (6개 테스트 모두). 실패하면 `co.elastic.clients` 쿼리 DSL 빌더 메서드 이름(`knn`, `queryVector` 등)이 실제 resolve된 elasticsearch-java 클라이언트 버전과 다른지 확인 — Global Constraints 참고.

- [ ] **Step 8: 색인→검색 통합 테스트 작성 (실제 ES, `EmbeddingModel`은 결정적인 스텁)**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterIntegrationTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 OpenAI를 호출하지 않는다 — EmbeddingModel을 "입력 문자열의 첫 글자로 방향이 갈리는"
 * 결정적 벡터를 만드는 스텁으로 교체해서, nori 매치와 kNN 둘 다 검증 가능하게 한다.
 */
@SpringBootTest
class ProjectSearchAdapterIntegrationTest extends ElasticsearchIntegrationTestSupport {

    @TestConfiguration
    static class StubEmbeddingConfig {
        @Bean
        EmbeddingModel embeddingModel() {
            EmbeddingModel stub = mock(EmbeddingModel.class);
            when(stub.embed(any(String.class))).thenAnswer(invocation -> {
                String text = invocation.getArgument(0);
                float[] vector = new float[1536];
                vector[0] = text.hashCode() % 1000 / 1000f;
                return vector;
            });
            return stub;
        }
    }

    @Autowired
    private ProjectSearchAdapter adapter;
    @Autowired
    private CircuitBreakerFactory circuitBreakerFactory;

    private Project project(Long id, String title) {
        Project project = Project.register(1L, null, title, 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        org.springframework.test.util.ReflectionTestUtils.setField(project, "projectId", id);
        return project;
    }

    @Test
    @DisplayName("색인한 프로젝트를 제목 키워드로 검색하면 찾아진다")
    void index_then_search_findsByKeyword() {
        adapter.index(project(100L, "한국어 형태소 분석 테스트 프로젝트"));
        adapter.index(project(200L, "완전히 다른 내용의 프로젝트"));

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("분석");
            assertThat(result).contains(100L);
            assertThat(result).doesNotContain(200L);
        });
    }

    @Test
    @DisplayName("삭제한 프로젝트는 더 이상 검색되지 않는다")
    void remove_thenNotFoundBySearch() {
        adapter.index(project(300L, "삭제될 프로젝트 키워드테스트"));
        await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.search("키워드테스트")).contains(300L));

        adapter.remove(300L);

        await().atMost(java.time.Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.search("키워드테스트")).doesNotContain(300L));
    }
}
```

이 테스트는 `org.awaitility:awaitility`가 필요하다 — ES는 색인 후 검색 가능해지기까지 최대 1초(refresh_interval) 정도 지연이 있을 수 있어서다. `project-service/build.gradle`에 추가:
```groovy
    testImplementation 'org.awaitility:awaitility'
```

- [ ] **Step 9: 통합 테스트 실행**

Run: `./gradlew :project-service:test --tests "ProjectSearchAdapterIntegrationTest"`
Expected: PASS

- [ ] **Step 10: 전체 테스트 스위트가 여전히 통과하는지 확인**

Run: `./gradlew :project-service:test`
Expected: BUILD SUCCESSFUL (기존 테스트들은 아직 `ProjectSearchAdapter`를 참조하지 않으므로 영향 없음)

- [ ] **Step 11: 커밋**

```bash
git add project-service/build.gradle \
        project-service/src/test/resources/application.yml \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/ProjectSearchPort.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapter.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/search/ProjectSearchAdapterIntegrationTest.java
git commit -m "$(cat <<'EOF'
Feat: 프로젝트 검색 ProjectSearchPort/Adapter 추가 (nori+kNN 하이브리드)

Spring AI EmbeddingModel로 OpenAI 임베딩을 생성하고, ES bool 쿼리로
nori 키워드 매치와 kNN 벡터 검색을 함께 실행한다. 색인/삭제 실패는
로그만 남기고 삼키고(원본 트랜잭션 보호), 검색 실패만 503으로 전파한다.
EOF
)"
```

---

### Task 3: `ProjectServiceImpl` 배선 — 색인 훅 + `findAll` keyword 라우팅

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplDeleteTest.java`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplCancelTest.java`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplOwnershipTest.java`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReconciliationTest.java`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplRetryTest.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplSearchIndexingTest.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplFindAllSearchTest.java`

**Interfaces:**
- Consumes: `ProjectSearchPort`(Task 2) — `void index(Project)`, `void remove(Long)`, `List<Long> search(String)`

**주의:** `ProjectServiceImpl`은 `@RequiredArgsConstructor`라서 필드 추가 = 생성자 파라미터 추가다. 5개 테스트 파일이 `new ProjectServiceImpl(...)`을 직접 호출하므로, `searchPort` 필드를 **맨 끝에** 추가해 기존 인자 순서를 안 건드린다. 이 Step들을 빠뜨리면 컴파일이 깨진다(과거에 실제로 겪은 실수 패턴 — 인터페이스 메서드 하나가 조용히 사라져서 `@Override`가 깨졌던 사고와 같은 종류의, "한 파일만 고치고 나머지 호출부를 놓치는" 실수).

- [ ] **Step 1: 실패하는 테스트 먼저 작성 — 색인 훅**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplSearchIndexingTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectCreateRequest;
import com.growmighty.lectures.firstday.project.project.presentation.dto.request.ProjectUpdateRequest;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** create/update/delete가 색인을 정확한 지점에서 호출하는지만 검증한다(Mockito, Spring 컨텍스트 불필요). */
class ProjectServiceImplSearchIndexingTest {

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
    private Project project;

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
        project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("프로젝트 생성 시 색인한다")
    void create_indexesProject() {
        ProjectCreateRequest request = new ProjectCreateRequest(1L, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectCategoryRepository.existsById(1L)).thenReturn(true);
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        projectService.create(1L, request);

        verify(searchPort).index(project);
    }

    @Test
    @DisplayName("프로젝트 수정 시 재색인한다")
    void update_reindexesProject() {
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        ProjectUpdateRequest request = new ProjectUpdateRequest(
                null, null, "new summary", null, null, null, null, null);

        projectService.update(1L, 1L, request);

        verify(searchPort).index(project);
    }

    @Test
    @DisplayName("프로젝트 삭제 시 색인에서도 제거한다")
    void delete_removesFromIndex() {
        when(projectRepository.findByIdForDelete(1L)).thenReturn(Optional.of(project));
        when(orderPort.hasOrderedReward(1L)).thenReturn(false);

        projectService.delete(1L, 1L);

        verify(searchPort).remove(1L);
    }
}
```

> `ProjectUpdateRequest`의 실제 생성자 인자 개수/순서가 위와 다르면(레코드 필드), 실행 시 컴파일 에러로 바로 드러난다 — `project-service/.../project/presentation/dto/request/ProjectUpdateRequest.java`를 열어 실제 필드 순서에 맞게 고친다.

- [ ] **Step 2: `findAll` keyword 라우팅 실패하는 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplFindAllSearchTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplFindAllSearchTest {

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
    @DisplayName("keyword가 없으면 ES를 호출하지 않는다")
    void findAll_noKeyword_doesNotCallSearchPort() {
        when(projectRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        projectService.findAll(null, null, null, null, UserRole.BACKER);

        verify(searchPort, never()).search(any());
    }

    @Test
    @DisplayName("keyword가 있으면 ES 검색 결과로 후보를 좁혀 MySQL에서 최종 조회한다")
    void findAll_withKeyword_routesThroughSearchPort() {
        when(searchPort.search("텀블러")).thenReturn(List.of(1L, 2L));
        when(projectRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        projectService.findAll("텀블러", null, null, null, UserRole.BACKER);

        verify(searchPort).search("텀블러");
    }

    @Test
    @DisplayName("ES 매치가 하나도 없으면 MySQL을 조회하지 않고 즉시 빈 리스트를 반환한다")
    void findAll_noMatches_returnsEmptyWithoutQueryingMySql() {
        when(searchPort.search("존재안함")).thenReturn(List.of());

        List<ProjectResponse> result = projectService.findAll("존재안함", null, null, null, UserRole.BACKER);

        assertThat(result).isEmpty();
        verify(projectRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("ES 검색이 실패하면 폴백 없이 503이 그대로 전파된다")
    void findAll_searchFails_propagatesServiceUnavailable() {
        when(searchPort.search("키워드")).thenThrow(new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다."));

        assertThatThrownBy(() -> projectService.findAll("키워드", null, null, null, UserRole.BACKER))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(projectRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }
}
```

- [ ] **Step 3: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `constructor ProjectServiceImpl in class ProjectServiceImpl cannot be applied to given types` (6개 인자로 호출하는데 현재 5개만 받음)

- [ ] **Step 4: `ProjectServiceImpl`에 `searchPort` 필드 추가 + `create`/`update`/`delete` 훅**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java` 수정:

임포트 추가:
```java
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
```

필드 추가 (`orderPort` 바로 아래, 맨 끝):
```java
    private final OrderPort orderPort;
    private final ProjectSearchPort searchPort;
```

`create` 수정:
```java
    @Override
    @Transactional
    public ProjectResponse create(Long creatorId, ProjectCreateRequest request) {
        validateCategoryExists(request.categoryId());
        Project project = projectRepository.save(request.toEntity(creatorId));
        searchPort.index(project);
        return ProjectResponse.from(project);
    }
```

`update` 수정 (메서드 끝, `return` 직전에 추가):
```java
            project.updateBeforePublish(request.title(), request.categoryId(), request.summary(), request.description(),
                    request.thumbnailId(), request.goalAmount(), request.startAt(), request.endAt());
        }
        searchPort.index(project);
        return ProjectResponse.from(project);
    }
```

`delete` 수정 (실제 삭제 직후):
```java
        rewardServiceProvider.getObject().deleteAllByProject(projectId);
        projectRepository.delete(project);
        searchPort.remove(projectId);
    }
```

- [ ] **Step 5: `findAll` keyword 라우팅 + `buildSpecification`에서 LIKE 분기 제거**

`findAll` 전체 교체:
```java
    @Override
    public List<ProjectResponse> findAll(String keyword, Long categoryId, ProjectStatus status, ProjectSort sort, UserRole requesterRole) {
        List<Long> candidateProjectIds = null;
        if (keyword != null && !keyword.isBlank()) {
            candidateProjectIds = searchPort.search(keyword);
            if (candidateProjectIds.isEmpty()) {
                return List.of();
            }
        }
        Specification<Project> specification = buildSpecification(candidateProjectIds, categoryId, status, requesterRole);
        ProjectSort effectiveSort = sort != null ? sort : ProjectSort.LATEST;
        return projectRepository.findAll(specification, effectiveSort.toSort()).stream()
                .map(ProjectResponse::from)
                .toList();
    }
```

`buildSpecification` 시그니처에서 `keyword`를 `candidateProjectIds`로 교체하고 LIKE 분기를 IN 분기로 교체:
```java
    private Specification<Project> buildSpecification(List<Long> candidateProjectIds, Long categoryId, ProjectStatus status, UserRole requesterRole) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (requesterRole != UserRole.ADMIN) {
                predicates.add(cb.and(
                        cb.notEqual(root.get("status"), ProjectStatus.PENDING_REVIEW),
                        cb.notEqual(root.get("status"), ProjectStatus.REJECTED)));
            }
            if (candidateProjectIds != null) {
                predicates.add(root.get("projectId").in(candidateProjectIds));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
```

- [ ] **Step 6: 기존 테스트 5개의 생성자 호출에 `searchPort` 인자 추가**

`ProjectServiceImplDeleteTest.java` — import에 `com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort` 추가, 필드에 `private final ProjectSearchPort searchPort = mock(ProjectSearchPort.class);` 추가, 생성자 호출 수정:
```java
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
```

`ProjectServiceImplCancelTest.java` — 같은 패턴, 44행:
```java
            new ProjectServiceImpl(projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
```

`ProjectServiceImplOwnershipTest.java` — 같은 패턴, 49행:
```java
            new ProjectServiceImpl(projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
```

`ProjectServiceImplReconciliationTest.java` — 같은 패턴, 61행:
```java
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
```

`ProjectServiceImplRetryTest.java` — `RetryTestConfig` 내부 `@Bean`을 하나 추가하고 `projectService(...)` 빈 메서드 시그니처에 파라미터 추가:
```java
        @Bean
        ProjectSearchPort projectSearchPort() {
            return mock(ProjectSearchPort.class);
        }

        @Bean
        ProjectService projectService(ProjectRepository projectRepository, ProjectCategoryRepository projectCategoryRepository,
                                       ObjectProvider<ProjectService> selfProvider, ObjectProvider<RewardService> rewardServiceProvider,
                                       OrderPort orderPort, ProjectSearchPort searchPort) {
            return new ProjectServiceImpl(projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort);
        }
```
(import에 `com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort` 추가)

- [ ] **Step 7: 새 테스트 + 기존 전체 테스트 실행**

Run: `./gradlew :project-service:test --tests "ProjectServiceImplSearchIndexingTest" --tests "ProjectServiceImplFindAllSearchTest"`
Expected: PASS

Run: `./gradlew :project-service:test`
Expected: BUILD SUCCESSFUL (기존 `ProjectServiceImplDeleteTest`/`CancelTest`/`OwnershipTest`/`ReconciliationTest`/`RetryTest`/`ProjectControllerTest` 등 전부 포함)

- [ ] **Step 8: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplDeleteTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplCancelTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplOwnershipTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReconciliationTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplRetryTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplSearchIndexingTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplFindAllSearchTest.java
git commit -m "$(cat <<'EOF'
Feat: findAll keyword 검색을 ES 하이브리드 검색으로 교체

create/update/delete가 ProjectSearchPort로 색인을 갱신하고, findAll의
keyword 분기는 기존 LIKE 대신 ES 검색 결과(candidateProjectIds)를
MySQL Specification의 IN 조건으로 넘겨 최종 필터링/정렬한다.
EOF
)"
```

---

### Task 4: 관리자 재색인 엔드포인트

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalController.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReindexTest.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalControllerReindexTest.java`

**Interfaces:**
- Consumes: `ProjectSearchPort.index(Project)`(Task 2)
- Produces: `ProjectService.reindexAllProjects()` — 다른 태스크가 의존하지 않는 최종 관리 기능.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReindexTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceImplReindexTest {

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
    @DisplayName("전체 프로젝트를 순회하며 재색인한다")
    void reindexAllProjects_indexesEveryProject() {
        Project p1 = Project.register(1L, null, "title1", 1L, "s", "d",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        Project p2 = Project.register(1L, null, "title2", 1L, "s", "d",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        when(projectRepository.findAll()).thenReturn(List.of(p1, p2));

        projectService.reindexAllProjects();

        verify(searchPort, times(1)).index(p1);
        verify(searchPort, times(1)).index(p2);
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `cannot find symbol: method reindexAllProjects()`

- [ ] **Step 3: `ProjectService` 인터페이스에 메서드 추가**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java`의 `findStatusView` 선언 위(파일 끝 쪽)에 추가:
```java
    /** 관리자 전용: ES 검색 인덱스가 MySQL과 어긋났을 때 전체를 다시 색인한다(백필/복구). */
    void reindexAllProjects();
```

- [ ] **Step 4: `ProjectServiceImpl`에 구현 추가**

`reconcileFundedAmounts()` 메서드 바로 아래에 추가 (읽기 전용 루프라 `reconcileFundedAmounts()`와 달리 재시도/NOT_SUPPORTED 불필요 — `searchPort.index()`는 절대 예외를 안 던지므로 프로젝트 하나 실패해도 나머지에 영향 없음):
```java
    @Override
    public void reindexAllProjects() {
        for (Project project : projectRepository.findAll()) {
            searchPort.index(project);
        }
    }
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew :project-service:test --tests "ProjectServiceImplReindexTest"`
Expected: PASS

- [ ] **Step 6: 컨트롤러 실패하는 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalControllerReindexTest.java`:
```java
package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectInternalControllerReindexTest {

    private final ProjectService projectService = mock(ProjectService.class);
    private final ProjectInternalController controller = new ProjectInternalController(projectService);

    @Test
    @DisplayName("재색인 요청을 받으면 reindexAllProjects()를 호출한다")
    void reindex_callsReindexAllProjects() {
        controller.reindex();

        verify(projectService).reindexAllProjects();
    }
}
```

- [ ] **Step 7: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `cannot find symbol: method reindex()`

- [ ] **Step 8: 컨트롤러에 엔드포인트 추가**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalController.java`에 임포트 추가:
```java
import org.springframework.web.bind.annotation.PostMapping;
```

`getCreator` 메서드 아래에 추가:
```java
    /** 관리자 전용: ES 검색 인덱스가 MySQL과 어긋났을 때(장애 복구, 최초 도입) 전체를 다시 색인한다. */
    @PostMapping("/reindex")
    public Void reindex() {
        projectService.reindexAllProjects();
        return null;
    }
```

- [ ] **Step 9: 전체 테스트 실행**

Run: `./gradlew :project-service:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: 전체 빌드 확인**

Run: `./gradlew :project-service:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalController.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReindexTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/presentation/ProjectInternalControllerReindexTest.java
git commit -m "$(cat <<'EOF'
Feat: 관리자 전용 프로젝트 검색 재색인 API 추가

POST /internal/v1/projects/reindex — ES 인덱스가 MySQL과 어긋났을 때
(장애 복구, 최초 도입) 전체 프로젝트를 다시 색인하는 백필용 내부 API.
EOF
)"
```

---

## Self-Review 결과 (계획 작성자 자체 점검)

- **스펙 커버리지:** 설계 문서의 요구사항 6개 항목(시그니처 유지/하이브리드 검색/keyword 없을 때 변경 없음/가시성·필터·정렬 유지/ES 장애 시 503/재색인 경로) 모두 Task 1~4에 대응하는 단계가 있다.
- **플레이스홀더 스캔:** "TODO"/"나중에" 형태 없음. 다만 Spring AI/Spring Data Elasticsearch의 정확한 fluent API 이름은 이 문서 작성 시점에 실행 검증이 안 됐다 — Global Constraints에 명시하고, 각 Task의 "테스트 실행" 단계가 이를 즉시 드러내도록 설계했다(플레이스홀더가 아니라 TDD 루프 자체가 검증 수단).
- **타입 일관성:** `ProjectSearchPort.index(Project)`/`.remove(Long)`/`.search(String): List<Long>` 시그니처가 Task 2(정의)~4(구현·사용) 전체에서 동일하게 쓰였다. `ProjectServiceImpl` 생성자 인자 순서(`projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort, searchPort`)도 Task 3의 모든 테스트 파일에서 동일하다.
- **범위 체크:** 이 계획은 project-service 한 모듈, 프로젝트 검색 기능 하나에 집중되어 있다 — 별도 하위 프로젝트로 쪼갤 필요 없음.

## 이 계획이 다루지 않는 것 (설계 문서의 "범위 밖"과 동일)

- `beadv7_7_earlybird_config` 리포에 실제 OpenAI API 키 등록 — 이 리포 밖의 별도 작업, 구현 착수 전 팀에 확인 필요.
- 관련도순(relevance) 정렬 옵션 추가, 전용 벡터DB 도입, reward/board 검색 — 설계 문서에서 이미 범위 밖으로 명시.
