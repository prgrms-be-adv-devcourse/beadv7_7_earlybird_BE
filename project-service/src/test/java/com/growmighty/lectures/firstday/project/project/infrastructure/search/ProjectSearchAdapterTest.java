package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderHttpClientTest와 같은 방식으로 CircuitBreaker.run(...)을 "그대로 실행"하도록 스텁한다.
 * index()/remove()는 이제 ES를 직접 부르지 않고 이벤트만 발행한다(실제 실행은
 * ProjectSearchIndexEventListener가 트랜잭션 커밋 후 applyIndex/applyRemove를 호출) — 그래서 이
 * 서킷브레이커 스텁은 search()와 applyIndex()/applyRemove() 세 곳 모두에 쓰인다.
 */
class ProjectSearchAdapterTest {

    private final ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
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
        adapter = new ProjectSearchAdapter(elasticsearchOperations, embeddingModel, circuitBreakerFactory, eventPublisher);
    }

    // 실전에선 index()가 불릴 때 project는 이미 저장돼 projectId가 채워져 있다(projectRepository.save()가
    // ID를 채운 뒤 반환) — 그 상태를 재현하려고 register() 직후 ID를 강제로 채운다.
    private Project project() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(project, "projectId", 1L);
        return project;
    }

    @Test
    @DisplayName("index()는 ES를 직접 부르지 않고 projectId만 담은 색인 요청 이벤트를 발행한다(내용은 나중에 다시 조회 — 멱등성)")
    void index_publishesIndexRequestedEvent() {
        adapter.index(project());

        verify(eventPublisher).publishEvent(new ProjectIndexRequestedEvent(project().getProjectId()));
        verifyNoInteractions(elasticsearchOperations, embeddingModel);
    }

    @Test
    @DisplayName("remove()는 ES를 직접 부르지 않고 삭제 요청 이벤트만 발행한다")
    void remove_publishesRemovedFromIndexEvent() {
        adapter.remove(1L);

        verify(eventPublisher).publishEvent(new ProjectRemovedFromIndexEvent(1L));
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("applyIndex()가 성공하면 (이벤트가 아니라) 넘겨받은 프로젝트 내용으로 임베딩을 생성해 ES에 저장한다")
    void applyIndex_success_savesDocument() {
        when(embeddingModel.embed("title summary desc")).thenReturn(new float[1536]);

        adapter.applyIndex(project());

        verify(elasticsearchOperations).save(any(ProjectDocument.class));
    }

    @Test
    @DisplayName("applyIndex() 중 임베딩 생성이나 ES 저장이 실패해도 예외를 던지지 않고 삼킨다(서킷브레이커 폴백)")
    void applyIndex_failure_doesNotThrow() {
        when(embeddingModel.embed(any(String.class))).thenThrow(new RuntimeException("openai down"));

        assertThatCode(() -> adapter.applyIndex(project())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("applyRemove()가 성공하면 ES에서 문서를 지운다")
    void applyRemove_success_deletesDocument() {
        adapter.applyRemove(1L);

        verify(elasticsearchOperations).delete("1", ProjectDocument.class);
    }

    @Test
    @DisplayName("applyRemove()가 실패해도 예외를 던지지 않고 삼킨다(서킷브레이커 폴백)")
    void applyRemove_failure_doesNotThrow() {
        when(elasticsearchOperations.delete("1", ProjectDocument.class)).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> adapter.applyRemove(1L)).doesNotThrowAnyException();
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
