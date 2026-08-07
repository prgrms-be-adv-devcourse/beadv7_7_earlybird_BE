package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.support.ElasticsearchIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

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
 * 실제 OpenAI를 호출하지 않는다 — EmbeddingModel을 텍스트별 결정적 랜덤 벡터를 만드는 스텁으로
 * 교체해서 nori 매치와 kNN 둘 다 검증 가능하게 한다. 벡터의 딱 한 축(index 0)만 채우면 코사인
 * 유사도가 두 텍스트 hashCode의 부호가 같은지만으로 항상 정확히 +1/-1이 되어버려(다른 축이 전부
 * 0이라 크기 비로 상쇄됨), 서로 무관한 텍스트끼리도 우연히 +1로 "완전 유사" 판정을 받을 수 있다.
 * 그래서 1536차원 전체를 텍스트 hashCode로 시드한 난수로 채운다 — 같은 텍스트는 항상 같은
 * 벡터(결정적)를 얻고, 다른 텍스트는 고차원 랜덤 벡터의 성질상 코사인 유사도가 0 근방에 몰려
 * ProjectSearchAdapter의 kNN 유사도 하한(0.5)을 사실상 통과하지 못한다.
 */
@SpringBootTest
class ProjectSearchAdapterIntegrationTest extends ElasticsearchIntegrationTestSupport {

    private static final int EMBEDDING_DIMENSIONS = 1536;

    @TestConfiguration
    static class StubEmbeddingConfig {
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
    private ProjectSearchAdapter adapter;

    private Project project(Long id, String title) {
        Project project = Project.register(1L, null, title, 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(project, "projectId", id);
        return project;
    }

    // ElasticsearchIntegrationTestSupport의 ES 컨테이너는 JVM당 싱글턴으로 여러 테스트 클래스가
    // 같은 "projects" 인덱스를 공유한다 — 여기서 색인한 문서를 안 지우면 다른 테스트 클래스의
    // 전체 인덱스 검색(예: ProjectSearchIndexBootstrapTest)에 우리 문서가 섞여 들어간다.
    @AfterEach
    void cleanUpIndexedDocuments() {
        adapter.remove(100L);
        adapter.remove(200L);
        adapter.remove(300L);
    }

    @Test
    @DisplayName("색인한 프로젝트를 제목 키워드로 검색하면 찾아진다")
    void index_then_search_findsByKeyword() {
        adapter.index(project(100L, "한국어 형태소 분석 테스트 프로젝트"));
        adapter.index(project(200L, "완전히 다른 내용의 프로젝트"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Long> result = adapter.search("분석");
            assertThat(result).contains(100L);
            assertThat(result).doesNotContain(200L);
        });
    }

    @Test
    @DisplayName("삭제한 프로젝트는 더 이상 검색되지 않는다")
    void remove_thenNotFoundBySearch() {
        adapter.index(project(300L, "삭제될 프로젝트 키워드테스트"));
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.search("키워드테스트")).contains(300L));

        adapter.remove(300L);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(adapter.search("키워드테스트")).doesNotContain(300L));
    }
}
