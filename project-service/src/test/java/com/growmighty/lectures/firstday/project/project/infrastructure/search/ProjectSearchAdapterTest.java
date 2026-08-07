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
import org.springframework.data.elasticsearch.core.query.Query;

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
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(hits);

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

    @Test
    @DisplayName("임베딩 생성은 성공했지만 ES 검색 호출 자체가 실패해도 503으로 변환한다 (LIKE 폴백 없음)")
    void search_elasticsearchCallFailure_throwsServiceUnavailable() {
        when(embeddingModel.embed("keyword")).thenReturn(new float[1536]);
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenThrow(new RuntimeException("es down"));

        assertThatThrownBy(() -> adapter.search("keyword"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
