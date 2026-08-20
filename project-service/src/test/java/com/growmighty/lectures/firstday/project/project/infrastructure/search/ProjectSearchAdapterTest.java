package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private final ElasticsearchClient elasticsearchClient = mock(ElasticsearchClient.class);
    private final CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProjectEmbeddingService embeddingService = mock(ProjectEmbeddingService.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectEmbeddingPersister persister = mock(ProjectEmbeddingPersister.class);
    private final ProjectCategoryRepository categoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private ProjectSearchAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(circuitBreakerFactory.create("projectSearch")).thenReturn(circuitBreaker);
        when(circuitBreakerFactory.create("projectAutocomplete")).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<Object> toRun = invocation.getArgument(0);
            Function<Throwable, Object> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        });
        adapter = new ProjectSearchAdapter(elasticsearchOperations, elasticsearchClient, circuitBreakerFactory, eventPublisher, embeddingService, projectRepository, persister, categoryRepository, rewardRepository);
    }

    private Project project() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(project, "projectId", 1L);
        return project;
    }

    @Test
    @DisplayName("index()는 ES를 직접 부르지 않고 projectId만 담은 색인 요청 이벤트를 발행한다")
    void index_publishesIndexRequestedEvent() {
        adapter.index(project());

        verify(eventPublisher).publishEvent(new ProjectIndexRequestedEvent(project().getProjectId()));
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("remove()는 ES를 직접 부르지 않고 삭제 요청 이벤트만 발행한다")
    void remove_publishesRemovedFromIndexEvent() {
        adapter.remove(1L);

        verify(eventPublisher).publishEvent(new ProjectRemovedFromIndexEvent(1L));
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("applyIndex()가 성공하면 넘겨받은 프로젝트 내용 및 저장된 임베딩 정보로 ES에 저장한다")
    void applyIndex_success_savesDocument() {
        adapter.applyIndex(project());

        verify(elasticsearchOperations).save(any(ProjectDocument.class));
    }

    @Test
    @DisplayName("applyIndex() 중 ES 저장이 실패해도 예외를 던지지 않고 삼킨다(서킷브레이커 폴백)")
    void applyIndex_failure_doesNotThrow() {
        when(elasticsearchOperations.save(any(ProjectDocument.class))).thenThrow(new RuntimeException("es down"));

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
    @DisplayName("임베딩이 사용 가능할 때 RRF 하이브리드 검색이 성공하면 매치된 문서들의 projectId를 반환한다")
    @SuppressWarnings("unchecked")
    void search_withEmbedding_rrfSuccess_returnsProjectIds() throws Exception {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(new float[1536]);
        SearchResponse<ProjectDocument> response = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> hitsMetadata = mock(HitsMetadata.class);
        Hit<ProjectDocument> hit = mock(Hit.class);
        when(hit.source()).thenReturn(new ProjectDocument(42L, "title", null, null, null, null, new float[1536]));
        when(hitsMetadata.hits()).thenReturn(List.of(hit));
        when(response.hits()).thenReturn(hitsMetadata);
        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(response);

        List<Long> result = adapter.search("keyword");

        assertThat(result).containsExactly(42L);
    }

    @Test
    @DisplayName("임베딩이 없을 때 BM25 검색이 성공하면 매치된 문서들의 projectId를 반환한다")
    @SuppressWarnings("unchecked")
    void search_withoutEmbedding_bm25Success_returnsProjectIds() {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(null);
        SearchHits<ProjectDocument> hits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(new ProjectDocument(42L, "title", null, null, null, null, null));
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(hit));
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(hits);

        List<Long> result = adapter.search("keyword");

        assertThat(result).containsExactly(42L);
    }

    @Test
    @DisplayName("ES 검색 호출이 실패하면 DB LIKE 검색으로 폴백한다")
    void search_elasticsearchCallFailure_fallsBackToDbLikeSearch() {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(null);
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenThrow(new RuntimeException("es down"));
        when(projectRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc("keyword"))
                .thenReturn(List.of(project()));

        List<Long> result = adapter.search("keyword");

        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("bulkIndex()는 임베딩이 없는 프로젝트만 새로 생성하고, 새 임베딩은 벌크로 DB에 반영한 뒤 ES에 일괄 저장한다")
    void bulkIndex_generatesMissingEmbeddingsAndSavesInBulk() {
        Project withoutEmbedding = project();
        float[] generated = new float[1536];
        when(embeddingService.generateEmbeddingForProject(withoutEmbedding)).thenReturn(generated);

        adapter.bulkIndex(List.of(withoutEmbedding));

        verify(persister).bulkUpdateEmbeddings(Map.of(withoutEmbedding.getProjectId(), generated));
        verify(elasticsearchOperations).save(anyList());
    }

    @Test
    @DisplayName("bulkIndex() 대상이 이미 임베딩을 갖고 있으면 새로 생성하지도, DB에 반영하지도 않는다")
    void bulkIndex_skipsEmbeddingGenerationWhenAlreadyPresent() {
        Project withEmbedding = project();
        withEmbedding.updateEmbedding(new float[1536]);

        adapter.bulkIndex(List.of(withEmbedding));

        verify(embeddingService, never()).generateEmbeddingForProject(any());
        verify(persister, never()).bulkUpdateEmbeddings(anyMap());
        verify(elasticsearchOperations).save(anyList());
    }

    @Test
    @DisplayName("bulkIndex() 중 ES 저장이 실패해도 예외를 던지지 않고 삼킨다(서킷브레이커 폴백)")
    void bulkIndex_failure_doesNotThrow() {
        when(elasticsearchOperations.save(anyList())).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> adapter.bulkIndex(List.of(project()))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("자동완성이 성공하면 매치된 문서들의 projectId와 title을 반환한다")
    @SuppressWarnings("unchecked")
    void autocomplete_success_returnsSuggestions() {
        SearchHits<ProjectDocument> hits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(new ProjectDocument(42L, "카카오 프로젝트", null, null, null, null, new float[1536]));
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
}
