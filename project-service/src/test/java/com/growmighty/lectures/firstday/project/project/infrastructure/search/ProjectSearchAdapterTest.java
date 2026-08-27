package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.UUID;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.util.ObjectBuilder;
import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectSearchAdapterTest {

    private final ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final ElasticsearchClient elasticsearchClient = mock(ElasticsearchClient.class);
    private final CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ProjectEmbeddingService embeddingService = mock(ProjectEmbeddingService.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository categoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final CategoryIntentResolver categoryIntentResolver = mock(CategoryIntentResolver.class);
    private final QueryIntentAnalyzer queryIntentAnalyzer = mock(QueryIntentAnalyzer.class);
    private ProjectSearchAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(circuitBreakerFactory.create("projectSearch")).thenReturn(circuitBreaker);
        when(circuitBreakerFactory.create("projectAutocomplete")).thenReturn(circuitBreaker);
        when(circuitBreakerFactory.create("projectBulkIndex")).thenReturn(circuitBreaker);
        // QueryIntentAnalyzer는 기본적으로 passThrough를 반환 — 기존 테스트 동작 유지
        when(queryIntentAnalyzer.analyze(any())).thenAnswer(inv -> QueryIntent.passThrough(inv.getArgument(0)));
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<Object> toRun = invocation.getArgument(0);
            Function<Throwable, Object> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        });
        adapter = new ProjectSearchAdapter(
                elasticsearchOperations, elasticsearchClient, circuitBreakerFactory,
                eventPublisher, embeddingService, projectRepository, categoryRepository,
                rewardRepository, categoryIntentResolver, queryIntentAnalyzer);
    }

    private Project project() {
        Project project = Project.register(1L, UUID.randomUUID(), null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        ReflectionTestUtils.setField(project, "projectId", 1L);
        return project;
    }

    private ProjectDocument sampleDocument(Long projectId, String title) {
        return new ProjectDocument(projectId, title, "summary", "desc", 1L, List.of("reward"),
                new float[1536], new float[1536], new float[1536], new float[1536], new float[1536]);
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
    @DisplayName("applyIndex()가 성공하면 5개 필드 벡터를 생성하여 ES에 저장한다")
    void applyIndex_success_savesDocument() {
        when(embeddingService.generateFieldVectors(any(), any(), any())).thenReturn(ProjectFieldVectors.empty());

        adapter.applyIndex(project());

        verify(elasticsearchOperations).save(any(ProjectDocument.class));
    }

    @Test
    @DisplayName("applyIndex() 중 ES 저장이 실패해도 예외를 던지지 않고 삼킨다(서킷브레이커 폴백)")
    void applyIndex_failure_doesNotThrow() {
        when(embeddingService.generateFieldVectors(any(), any(), any())).thenReturn(ProjectFieldVectors.empty());
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
    @DisplayName("임베딩이 사용 가능할 때 키워드 검색과 kNN 검색을 각각 호출해 Score-aware Hybrid 퓨전 결과를 반환한다")
    @SuppressWarnings("unchecked")
    void search_withEmbedding_scoreFusionSuccess_returnsCombinedResults() throws Exception {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(new float[1536]);
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of());

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        SearchHit<ProjectDocument> keywordHit = mock(SearchHit.class);
        when(keywordHit.getContent()).thenReturn(sampleDocument(42L, "keyword only"));
        when(keywordHit.getScore()).thenReturn(1.5f);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.of(keywordHit));
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits);

        SearchResponse<ProjectDocument> knnResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> knnHitsMetadata = mock(HitsMetadata.class);
        Hit<ProjectDocument> knnHit = mock(Hit.class);
        when(knnHit.source()).thenReturn(sampleDocument(7L, "knn only"));
        when(knnHit.score()).thenReturn(0.85); // ES cosine score (0.85 -> rawCosine 0.70)
        when(knnHitsMetadata.hits()).thenReturn(List.of(knnHit));
        when(knnResponse.hits()).thenReturn(knnHitsMetadata);
        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(knnResponse);

        List<Long> result = adapter.search("keyword");

        assertThat(result).containsExactlyInAnyOrder(42L, 7L);
    }

    @Test
    @DisplayName("Score 퓨전 시 높은 유사도의 벡터 매치 문서가 올바르게 랭킹에 반영된다")
    @SuppressWarnings("unchecked")
    void search_scoreFusion_prioritizesHigherRelevanceScores() throws Exception {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(new float[1536]);
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of());

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        SearchHit<ProjectDocument> keywordHit = mock(SearchHit.class);
        when(keywordHit.getContent()).thenReturn(sampleDocument(10L, "keyword match"));
        when(keywordHit.getScore()).thenReturn(2.0f);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.of(keywordHit));
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits);

        SearchResponse<ProjectDocument> knnResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> knnHitsMetadata = mock(HitsMetadata.class);
        Hit<ProjectDocument> knnHit = mock(Hit.class);
        when(knnHit.source()).thenReturn(sampleDocument(20L, "knn high score"));
        when(knnHit.score()).thenReturn(0.95); // ES cosine score (raw cosine 0.90)
        when(knnHitsMetadata.hits()).thenReturn(List.of(knnHit));
        when(knnResponse.hits()).thenReturn(knnHitsMetadata);

        SearchResponse<ProjectDocument> emptyResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> emptyHits = mock(HitsMetadata.class);
        when(emptyHits.hits()).thenReturn(List.of());
        when(emptyResponse.hits()).thenReturn(emptyHits);

        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(knnResponse, emptyResponse, emptyResponse, emptyResponse, emptyResponse);

        List<Long> result = adapter.search("keyword");

        assertThat(result).contains(10L, 20L);
    }

    @Test
    @DisplayName("임베딩이 없을 때 BM25 검색이 성공하면 매치된 문서들의 projectId를 반환한다")
    @SuppressWarnings("unchecked")
    void search_withoutEmbedding_bm25Success_returnsProjectIds() {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(null);
        SearchHits<ProjectDocument> hits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(sampleDocument(42L, "title"));
        when(hit.getScore()).thenReturn(1.0f);
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

    private ProjectCategory category(Long id, String name) {
        ProjectCategory category = ProjectCategory.create(null, name);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    @Test
    @DisplayName("카테고리명으로 시작하는 복합 검색어(\"반려동물 음식\")는 Exact Category Match가 아니다")
    @SuppressWarnings("unchecked")
    void search_categoryNamePrefixCompoundKeyword_isNotExactCategoryMatch() throws Exception {
        when(embeddingService.generateEmbedding("반려동물 음식")).thenReturn(new float[1536]);
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of());
        // "반려동물" 카테고리가 실제로 존재하는 상황 — 검색어가 이 이름으로 "시작"할 뿐, 전체가
        // 같지는 않다("반려동물 음식" != "반려동물"). 예전 startsWith() 로직이면 여기서 매칭됐다.
        when(categoryRepository.findAll()).thenReturn(List.of(category(13L, "반려동물")));

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits);

        SearchResponse<ProjectDocument> emptyResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> emptyHits = mock(HitsMetadata.class);
        when(emptyHits.hits()).thenReturn(List.of());
        when(emptyResponse.hits()).thenReturn(emptyHits);
        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(emptyResponse);

        adapter.search("반려동물 음식");

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = forClass(Function.class);
        verify(elasticsearchClient, times(5)).search(captor.capture(), eq(ProjectDocument.class));
        for (Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn : captor.getAllValues()) {
            SearchRequest request = SearchRequest.of(fn);
            assertThat(request.knn().get(0).filter())
                    .as("카테고리명으로 시작하는 복합어만으로는 kNN에 categoryId 하드 필터가 걸리면 안 된다")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("CategoryIntent가 오탐이어도(threshold를 근소하게 통과) kNN 검색에 하드 필터를 걸지 않는다")
    @SuppressWarnings("unchecked")
    void search_categoryIntentOnly_doesNotHardFilterKnn() throws Exception {
        when(embeddingService.generateEmbedding("간식")).thenReturn(new float[1536]);
        // "간식"이 실제로는 "상의"(categoryId=3)로 오판정된 상황을 재현
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of(3L));

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.empty());
        SearchHits<ProjectDocument> membershipHits = mock(SearchHits.class);
        when(membershipHits.stream()).thenReturn(java.util.stream.Stream.empty());
        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits, membershipHits);

        SearchResponse<ProjectDocument> emptyResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> emptyHits = mock(HitsMetadata.class);
        when(emptyHits.hits()).thenReturn(List.of());
        when(emptyResponse.hits()).thenReturn(emptyHits);
        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(emptyResponse);

        adapter.search("간식");

        ArgumentCaptor<Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>>> captor = forClass(Function.class);
        verify(elasticsearchClient, times(5)).search(captor.capture(), eq(ProjectDocument.class));
        for (Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn : captor.getAllValues()) {
            SearchRequest request = SearchRequest.of(fn);
            assertThat(request.knn()).hasSize(1);
            assertThat(request.knn().get(0).filter())
                    .as("Category Intent(추정치)만으로는 kNN에 categoryId 하드 필터가 걸리면 안 된다")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("CategoryIntent가 지목한 카테고리에 속한 후보는 소프트 부스트로 동점자 중 우선 랭크된다")
    @SuppressWarnings("unchecked")
    void search_categoryIntentMatch_boostsCandidateScore() throws Exception {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(new float[1536]);
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of(3L));

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hitA = mock(SearchHit.class);
        when(hitA.getContent()).thenReturn(sampleDocument(10L, "a"));
        when(hitA.getScore()).thenReturn(1.0f);
        SearchHit<ProjectDocument> hitB = mock(SearchHit.class);
        when(hitB.getContent()).thenReturn(sampleDocument(20L, "b"));
        when(hitB.getScore()).thenReturn(1.0f);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.of(hitA, hitB));

        SearchHits<ProjectDocument> membershipHits = mock(SearchHits.class);
        SearchHit<ProjectDocument> memberHit = mock(SearchHit.class);
        when(memberHit.getContent()).thenReturn(sampleDocument(10L, "a"));
        when(membershipHits.stream()).thenReturn(java.util.stream.Stream.of(memberHit));

        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits, membershipHits);

        SearchResponse<ProjectDocument> emptyResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> emptyHits = mock(HitsMetadata.class);
        when(emptyHits.hits()).thenReturn(List.of());
        when(emptyResponse.hits()).thenReturn(emptyHits);
        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(emptyResponse);

        List<Long> result = adapter.search("keyword");

        assertThat(result).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("카테고리 부스트만으로는 컷오프를 넘을 수 없다 — 실질 관련도가 낮은 후보(\"캣타워\" 격)는 제외된다")
    @SuppressWarnings("unchecked")
    void search_weakCandidateWithOnlyCategoryBoost_isExcludedByCutoff() throws Exception {
        when(embeddingService.generateEmbedding("keyword")).thenReturn(new float[1536]);
        when(categoryIntentResolver.resolveCategoryIntent(any())).thenReturn(List.of(3L));

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.empty());

        SearchHits<ProjectDocument> membershipHits = mock(SearchHits.class);
        SearchHit<ProjectDocument> memberHit = mock(SearchHit.class);
        when(memberHit.getContent()).thenReturn(sampleDocument(99L, "카테고리만 맞는 무관 상품"));
        when(membershipHits.stream()).thenReturn(java.util.stream.Stream.of(memberHit));

        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits, membershipHits);

        // rewardVector: 강한 후보(50L, raw cosine 0.90 -> *0.20 = 0.18)
        SearchResponse<ProjectDocument> strongKnnResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> strongHits = mock(HitsMetadata.class);
        Hit<ProjectDocument> strongHit = mock(Hit.class);
        when(strongHit.source()).thenReturn(sampleDocument(50L, "진짜 관련 상품"));
        when(strongHit.score()).thenReturn(0.95);
        when(strongHits.hits()).thenReturn(List.of(strongHit));
        when(strongKnnResponse.hits()).thenReturn(strongHits);

        // summaryVector: 약한 후보(99L, raw cosine 0.10 -> *0.12 = 0.012) — 카테고리 부스트 대상이기도 함
        SearchResponse<ProjectDocument> weakKnnResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> weakHits = mock(HitsMetadata.class);
        Hit<ProjectDocument> weakHit = mock(Hit.class);
        when(weakHit.source()).thenReturn(sampleDocument(99L, "카테고리만 맞는 무관 상품"));
        when(weakHit.score()).thenReturn(0.55);
        when(weakHits.hits()).thenReturn(List.of(weakHit));
        when(weakKnnResponse.hits()).thenReturn(weakHits);

        SearchResponse<ProjectDocument> emptyResponse = mock(SearchResponse.class);
        HitsMetadata<ProjectDocument> emptyHits = mock(HitsMetadata.class);
        when(emptyHits.hits()).thenReturn(List.of());
        when(emptyResponse.hits()).thenReturn(emptyHits);

        // doSearch() 순서: rewardVector, titleVector, categoryVector, summaryVector, descriptionVector
        when(elasticsearchClient.search(any(Function.class), eq(ProjectDocument.class)))
                .thenReturn(strongKnnResponse, emptyResponse, emptyResponse, weakKnnResponse, emptyResponse);

        List<Long> result = adapter.search("keyword");

        assertThat(result).contains(50L);
        assertThat(result).doesNotContain(99L);
    }

    @Test
    @DisplayName("QueryIntent/Vector Branch가 실패해도 BM25 결과만으로 안전하게 정상 검색된다 (Graceful Degradation)")
    @SuppressWarnings("unchecked")
    void search_whenVectorBranchFails_returnsBm25ResultsGracefully() {
        // QueryIntent/Embedding 실패 시뮬레이션
        when(queryIntentAnalyzer.analyze("강아지")).thenThrow(new RuntimeException("LLM Timeout"));
        when(embeddingService.generateEmbedding(any())).thenThrow(new RuntimeException("OpenAI API Down"));

        SearchHits<ProjectDocument> keywordHits = mock(SearchHits.class);
        SearchHit<ProjectDocument> hit1 = mock(SearchHit.class);
        when(hit1.getContent()).thenReturn(sampleDocument(100L, "강아지 수제 사료"));
        when(hit1.getScore()).thenReturn(10.0f);
        when(keywordHits.stream()).thenReturn(java.util.stream.Stream.of(hit1));

        when(elasticsearchOperations.search(any(Query.class), eq(ProjectDocument.class)))
                .thenReturn(keywordHits);

        List<Long> result = adapter.search("강아지");

        assertThat(result).containsExactly(100L);
    }

    @Test
    @DisplayName("bulkIndex()는 5개 필드 벡터를 일괄 생성하여 ES에 저장한다")
    void bulkIndex_generatesFieldVectorsAndSavesInBulk() {
        Project p = project();
        when(embeddingService.generateFieldVectorsBulk(any())).thenReturn(Map.of(p.getProjectId(), ProjectFieldVectors.empty()));

        adapter.bulkIndex(List.of(p));

        verify(elasticsearchOperations).save(anyList());
    }
}
