package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.project.presentation.dto.response.ProjectResponse;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 지금까지의 검색 관련 테스트는 전부 ES↔MySQL 경계 한쪽을 모킹했다:
 * {@code ProjectServiceImplFindAllSearchTest}는 searchPort와 projectRepository를 둘 다 모킹해서
 * buildSpecification()이 조립은 되지만 실제 데이터로 평가된 적이 없고, {@code ProjectSearchAdapterIntegrationTest}는
 * 실제 ES는 쓰지만 ProjectServiceImpl을 거치지 않는다. 그래서 이 기능의 가장 안전-결정적인 두 성질 —
 * "ES가 좁힌 후보가 실제 MySQL 쿼리를 진짜로 좁히는지"와 "role 기반 가시성(PENDING_REVIEW/REJECTED는
 * non-ADMIN에게 항상 숨김)이 keyword 검색 경로에서도 유지되는지" — 가 코드만 읽어서 맞다고 판단됐을 뿐
 * 실행으로 검증된 적이 없었다. 이 테스트는 실제 MySQL(Testcontainers) + 실제 nori ES(Testcontainers)를
 * 함께 띄워 그 경계 전체를 한 번에 태운다.
 */
@SpringBootTest
class ProjectServiceImplSearchIntegrationTest extends ElasticsearchIntegrationTestSupport {

    private static final int EMBEDDING_DIMENSIONS = 1536;
    private static final String KEYWORD = "은하탐사기획전";

    @TestConfiguration
    static class StubEmbeddingConfig {
        // ProjectSearchAdapterIntegrationTest와 같은 방식 — 텍스트별로 결정적인 1536차원 랜덤 벡터를
        // 만들어서 실제 OpenAI 호출 없이도 nori 매치/kNN 둘 다 이 테스트의 키워드에 대해 정상 동작하게 한다.
        @Bean
        EmbeddingModel embeddingModel() {
            EmbeddingModel stub = mock(EmbeddingModel.class);
            when(stub.embed(any(String.class))).thenAnswer(invocation -> {
                String text = invocation.getArgument(0);
                Random random = new Random(text.hashCode());
                float[] vector = new float[EMBEDDING_DIMENSIONS];
                for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
                    vector[i] = random.nextFloat() * 2f - 1f;
                }
                return vector;
            });
            return stub;
        }
    }

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectSearchPort searchPort;

    private Long approvedCategory1Id;
    private Long pendingCategory1Id;
    private Long approvedCategory2Id;

    // ElasticsearchIntegrationTestSupport의 ES 컨테이너는 JVM당 싱글턴으로 여러 테스트 클래스가 같은
    // "projects" 인덱스를 공유한다(ProjectSearchAdapterIntegrationTest 주석 참고) — 이 테스트가 색인한
    // 문서를 지워야 다른 테스트 클래스의 전체 인덱스 검색에 섞여 들어가지 않는다.
    @AfterEach
    void cleanUpIndexedDocuments() {
        if (approvedCategory1Id != null) {
            searchPort.remove(approvedCategory1Id);
        }
        if (pendingCategory1Id != null) {
            searchPort.remove(pendingCategory1Id);
        }
        if (approvedCategory2Id != null) {
            searchPort.remove(approvedCategory2Id);
        }
    }

    @Test
    @DisplayName("keyword+categoryId 검색은 ES로 후보를 좁힌 뒤에도 categoryId/role 필터가 실제 MySQL 쿼리에서 적용된다")
    void findAll_withKeywordAndCategory_appliesRealMySqlFilteringOnEsNarrowedCandidates() {
        // 셋 다 키워드는 매치하지만, categoryId/status가 서로 달라서 MySQL 쪽 필터가 실제로 걸러내야
        // 한다 — ES는 categoryId/status를 전혀 모르므로(설계상 의도적) 이 세 후보를 전부 반환해야 맞다.
        Project approvedCategory1 = savedProject(KEYWORD, 1L);
        approvedCategory1.approve();
        approvedCategory1 = projectRepository.save(approvedCategory1);
        approvedCategory1Id = approvedCategory1.getProjectId();

        Project pendingCategory1 = savedProject(KEYWORD, 1L);
        pendingCategory1Id = pendingCategory1.getProjectId();

        Project approvedCategory2 = savedProject(KEYWORD, 2L);
        approvedCategory2.approve();
        approvedCategory2 = projectRepository.save(approvedCategory2);
        approvedCategory2Id = approvedCategory2.getProjectId();

        searchPort.index(approvedCategory1);
        searchPort.index(pendingCategory1);
        searchPort.index(approvedCategory2);

        // 1) ES 자체는 categoryId/status와 무관하게 키워드 매치만으로 셋 다 후보로 돌려줘야 한다 —
        //    이래야 뒤이은 결과 차이가 "ES가 이미 걸러줬다"가 아니라 "MySQL이 걸러냈다"임이 보장된다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> candidates = searchPort.search(KEYWORD);
            assertThat(candidates).contains(approvedCategory1Id, pendingCategory1Id, approvedCategory2Id);
        });

        // 2) 실제 서비스 경로: keyword + categoryId=1 + BACKER(non-ADMIN) — 승인된 category-1 프로젝트만
        //    남아야 한다. pendingCategory1은 role 가시성 규칙(PENDING_REVIEW는 non-ADMIN에게 항상 숨김)에
        //    걸리고, approvedCategory2는 categoryId 불일치로 걸린다 — 둘 다 MySQL 쪽 필터다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<ProjectResponse> result = projectService.findAll(KEYWORD, 1L, null, null, UserRole.BACKER);
            assertThat(result).extracting(ProjectResponse::projectId).containsExactly(approvedCategory1Id);
        });
    }

    private Project savedProject(String keyword, Long categoryId) {
        Project project = Project.register(1L, null, keyword + " 프로젝트", categoryId,
                keyword + " 요약", keyword + " 상세 설명", BigDecimal.valueOf(1_000_000),
                LocalDateTime.now(), LocalDate.now().plusDays(30));
        return projectRepository.save(project);
    }
}
