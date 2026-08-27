package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Elasticsearch 검색 포트 구현체.
 * 5개 독립 벡터(title, summary, description, category, reward)의 코사인 점수 보존 kNN 검색과
 * Nori 형태소 분석기 기반 BM25 키워드 검색을 Score-aware Hybrid Fusion으로 결합하고,
 * 2-Stage Query-Product Compatibility Layer를 통해 의미적 적합성 및 충돌 여부를 종합 평가하여 최종 순위를 산출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private static final String INDEX_NAME = "projects";
    private static final int MAX_RESULTS = 200;

    /** Score-aware Hybrid Ranking 가중치 (총합 = 1.00) */
    private static final double BM25_WEIGHT = 0.20;
    private static final double TITLE_VECTOR_WEIGHT = 0.25;
    private static final double REWARD_VECTOR_WEIGHT = 0.20;
    private static final double CATEGORY_VECTOR_WEIGHT = 0.15;
    private static final double SUMMARY_VECTOR_WEIGHT = 0.12;
    private static final double DESCRIPTION_VECTOR_WEIGHT = 0.08;

    /** 최고 점수 대비 하위 노이즈 문서 동적 차단 비율 (1위 문서 점수 대비 35% 미만 점수 제거) */
    private static final double RELATIVE_SCORE_CUTOFF_RATIO = 0.35;
    /** kNN 벡터 유사도 하한: 완전 무관한 노이즈 문서(코사인 유사도 0.38 미만) 차단 */
    private static final float MIN_KNN_SIMILARITY = 0.38f;
    /** 최종 점수 절대 하한 */
    private static final double MIN_FINAL_SCORE = 0.01;
    /** ES 자동완성 후보 과다조회 한도 */
    private static final int AUTOCOMPLETE_CANDIDATE_LIMIT = 50;
    /** 형태소 어휘 레벨 최소 일치 조건 */
    private static final String MATCH_MINIMUM_SHOULD_MATCH = "2<70%";
    /**
     * BM25/kNN 완료 후 QueryIntent(LLM)를 Compatibility 평가(계절 충돌 상품 제거 등)에 합류시키기 위해
     * 기다리는 예산(ms). 임베딩·kNN은 LLM을 전혀 기다리지 않고 원본 쿼리로 즉시 진행하므로(크리티컬 패스에서
     * 제외), cold 검색의 실질 지연은 이 예산과 LLM 지연 중 큰 쪽에 수렴한다. 예산은
     * projectQueryIntent TimeLimiter(4s)와 맞춰, LLM이 정상 응답하면(느린 날 포함) 계절 충돌 제거가
     * 항상 동작하고, LLM 행업 시에만 4s 뒤 Compatibility 없이 완주한다(그 뒤 CB가 열려 후속 검색은 빠름).
     */
    private static final long INTENT_JOIN_BUDGET_MS = 4000;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;
    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository categoryRepository;
    private final RewardRepository rewardRepository;
    private final CategoryIntentResolver categoryIntentResolver;
    private final QueryIntentAnalyzer queryIntentAnalyzer;
    private final QueryProductCompatibilityEvaluator compatibilityEvaluator;
    private final java.util.concurrent.Executor searchTaskExecutor;

    @Override
    public void index(Project project) {
        eventPublisher.publishEvent(new ProjectIndexRequestedEvent(project.getProjectId()));
    }

    @Override
    public void remove(Long projectId) {
        eventPublisher.publishEvent(new ProjectRemovedFromIndexEvent(projectId));
    }

    void applyIndex(Project project) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> {
                    List<String> rewardNames = rewardRepository.findByProjectId(project.getProjectId()).stream()
                            .map(Reward::getName)
                            .toList();
                    String categoryHierarchy = resolveCategoryHierarchy(project.getCategoryId());
                    ProjectFieldVectors vectors = embeddingService.generateFieldVectors(project, categoryHierarchy, rewardNames);
                    elasticsearchOperations.save(toDocument(project, rewardNames, vectors));
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), cause);
                    return null;
                });
    }

    private ProjectDocument toDocument(Project project, List<String> rewardNames, ProjectFieldVectors vectors) {
        return new ProjectDocument(
                project.getProjectId(),
                project.getTitle(),
                project.getSummary(),
                project.getDescription(),
                project.getCategoryId(),
                rewardNames,
                vectors.titleVector(),
                vectors.summaryVector(),
                vectors.descriptionVector(),
                vectors.categoryVector(),
                vectors.rewardVector()
        );
    }

    void applyRemove(Long projectId) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> {
                    elasticsearchOperations.delete(String.valueOf(projectId), ProjectDocument.class);
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 삭제 실패. projectId={}", projectId, cause);
                    return null;
                });
    }

    @Override
    public List<Long> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> doSearch(keyword),
                cause -> searchFallback(keyword, cause));
    }

    @Override
    public List<ProjectSuggestion> autocomplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_AUTOCOMPLETE_ID).run(
                () -> doAutocomplete(prefix),
                this::autocompleteFallback);
    }

    private static final Map<String, List<String>> SLANG_SYNONYM_MAP = Map.of(
            "냥이", List.of("고양이"),
            "댕댕이", List.of("강아지"),
            "공청기", List.of("공기청정기"),
            "폰케이스", List.of("스마트폰 케이스")
    );

    private List<String> resolveSlangSynonyms(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String trimmed = keyword.trim();
        List<String> synonyms = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : SLANG_SYNONYM_MAP.entrySet()) {
            if (trimmed.contains(entry.getKey())) {
                synonyms.addAll(entry.getValue());
            }
        }
        return synonyms;
    }

    private List<Long> doSearch(String keyword) {
        long totalStart = System.currentTimeMillis();
        String trimmedKeyword = keyword.trim();

        // ── 1. 공통 전처리: 정확한 카테고리명 및 슬랭 동의어 (초고속 인메모리, < 1ms) ───
        List<Long> exactCategoryIds = resolveExactCategoryIds(trimmedKeyword);
        List<String> slangSynonyms = resolveSlangSynonyms(trimmedKeyword);

        if (!exactCategoryIds.isEmpty()) {
            log.info("[ProjectSearch] 키워드='{}' → 카테고리명 일치로 kNN 하드 스코프 적용: categoryIds={}", trimmedKeyword, exactCategoryIds);
        }

        // ── QueryIntent(LLM) 분석: BM25/임베딩/kNN과 동시에 격발하고 어느 것도 이걸 기다리지 않는다.
        //   · enrichedQuery 재작성: 임베딩 시점에 이미 끝나 있으면(캐시 히트) 조기 반영, 아니면 원본 쿼리 사용
        //   · requirements 기반 Compatibility 평가(계절 충돌 제거 등): fusion 직전 INTENT_JOIN_BUDGET_MS 예산으로 합류
        long llmStart = System.currentTimeMillis();
        CompletableFuture<QueryIntent> intentFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return queryIntentAnalyzer.analyze(trimmedKeyword);
            } catch (Exception e) {
                log.warn("[ProjectSearch] QueryIntent 분석 중 예외 발생, passThrough 폴백: {}", e.getMessage());
                return QueryIntent.passThrough(trimmedKeyword);
            }
        }, searchTaskExecutor);

        // ── 2. [Branch 1: Fast BM25 (비동기 병렬 실행)] ─────────────────────────────
        CompletableFuture<List<ScoredDocument>> bm25Future = CompletableFuture.supplyAsync(() -> {
            long bm25Start = System.currentTimeMillis();
            log.debug("[ProjectSearch Diagnostic] BM25 START on thread: {}", Thread.currentThread().getName());
            Query keywordQuery = Query.of(q -> q.bool(b -> {
                b.should(s -> s.match(m -> m.field("title").query(trimmedKeyword).boost(2.0f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("summary").query(trimmedKeyword).boost(1.2f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("description").query(trimmedKeyword).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("rewardNames").query(trimmedKeyword).boost(1.5f)));

                if (!slangSynonyms.isEmpty()) {
                    String slangQuery = String.join(" ", slangSynonyms);
                    b.should(s -> s.match(m -> m.field("title").query(slangQuery).boost(0.4f)))
                            .should(s -> s.match(m -> m.field("rewardNames").query(slangQuery).boost(0.4f)));
                }

                if (!exactCategoryIds.isEmpty()) {
                    b.should(s -> s.matchAll(m -> m));
                    b.filter(f -> f.terms(t -> t.field("categoryId")
                            .terms(ts -> ts.value(exactCategoryIds.stream().map(FieldValue::of).toList()))));
                }
                return b.minimumShouldMatch("1");
            }));

            List<ScoredDocument> docs = searchKeywordScored(keywordQuery);
            long bm25Elapsed = System.currentTimeMillis() - bm25Start;
            log.debug("[ProjectSearch Latency] BM25 END: {}ms, 매칭 {}건 on thread: {}", bm25Elapsed, docs.size(), Thread.currentThread().getName());
            return docs;
        }, searchTaskExecutor);

        // ── 3. [Branch 2: Embedding → kNN (비동기 병렬 실행, LLM을 기다리지 않음)] ──────────
        record VectorBranchResult(
                List<ScoredDocument> rewardScored,
                List<ScoredDocument> titleScored,
                List<ScoredDocument> categoryVectorScored,
                List<ScoredDocument> summaryScored,
                List<ScoredDocument> descScored,
                Set<Long> categoryIntentBoostProjectIds,
                long embeddingElapsed,
                long knnElapsed
        ) {}

        CompletableFuture<VectorBranchResult> vectorFuture = CompletableFuture.supplyAsync(() -> {
            log.debug("[ProjectSearch Diagnostic] Vector Branch START on thread: {}", Thread.currentThread().getName());
            // (1) LLM을 기다리지 않는다 — 이미 준비된 enrichedQuery(캐시 히트 등)만 조기 반영하고 아니면 원본 쿼리로 즉시 진행.
            QueryIntent readyIntent = intentFuture.getNow(null);
            boolean useEnriched = readyIntent != null && readyIntent.hasStructuredIntent()
                    && readyIntent.enrichedQuery() != null && !readyIntent.enrichedQuery().isBlank();
            String queryForEmbedding = useEnriched ? readyIntent.enrichedQuery() : trimmedKeyword;
            if (useEnriched) {
                log.info("[ProjectSearch] Query Intent 조기 반영: query='{}' → enrichedQuery='{}' (target={}, requirementsCount={})",
                        trimmedKeyword, queryForEmbedding, readyIntent.target(), readyIntent.requirements().size());
            }

            // (2) 임베딩 생성 (OpenAI Embedding API)
            long embStart = System.currentTimeMillis();
            log.debug("[ProjectSearch Diagnostic] Embedding START for query: '{}' on thread: {}", queryForEmbedding, Thread.currentThread().getName());
            float[] queryVector = null;
            try {
                queryVector = embeddingService.generateEmbedding(queryForEmbedding);
            } catch (Exception e) {
                log.warn("[ProjectSearch] 임베딩 생성 실패: {}", e.getMessage());
            }
            long embeddingElapsed = System.currentTimeMillis() - embStart;
            log.debug("[ProjectSearch Diagnostic] Embedding END: {}ms on thread: {}", embeddingElapsed, Thread.currentThread().getName());

            if (queryVector == null || queryVector.length == 0) {
                return new VectorBranchResult(
                        List.of(), List.of(), List.of(), List.of(), List.of(), Set.of(),
                        embeddingElapsed, 0);
            }

            // (3) Category Intent 추론
            List<Long> intentCategoryIds = exactCategoryIds.isEmpty()
                    ? categoryIntentResolver.resolveCategoryIntent(queryVector)
                    : List.of();

            if (exactCategoryIds.isEmpty()) {
                if (!intentCategoryIds.isEmpty()) {
                    log.info("[ProjectSearch] 키워드='{}' → 카테고리 의도 소프트 부스트 적용: categoryIds={}", trimmedKeyword, intentCategoryIds);
                } else {
                    log.info("[ProjectSearch] 키워드='{}' → 카테고리 스코프 미적용 (전체 검색)", trimmedKeyword);
                }
            }

            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float f : queryVector) {
                vectorList.add(f);
            }

            // (4) 5개 필드 kNN 검색 병렬 격발
            long knnStart = System.currentTimeMillis();
            log.debug("[ProjectSearch Diagnostic] kNN 5-field START on thread: {}", Thread.currentThread().getName());
            CompletableFuture<List<ScoredDocument>> rewardFuture = CompletableFuture.supplyAsync(
                    () -> searchFieldKnnScored("rewardVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> titleFuture = CompletableFuture.supplyAsync(
                    () -> searchFieldKnnScored("titleVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> catVecFuture = CompletableFuture.supplyAsync(
                    () -> searchFieldKnnScored("categoryVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> summaryFuture = CompletableFuture.supplyAsync(
                    () -> searchFieldKnnScored("summaryVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);
            CompletableFuture<List<ScoredDocument>> descFuture = CompletableFuture.supplyAsync(
                    () -> searchFieldKnnScored("descriptionVector", vectorList, 20, 50, exactCategoryIds), searchTaskExecutor);

            log.debug("[ProjectSearch Diagnostic] Vector outer task BEFORE JOIN for kNN 5-field on thread: {}", Thread.currentThread().getName());
            CompletableFuture.allOf(rewardFuture, titleFuture, catVecFuture, summaryFuture, descFuture).join();
            log.debug("[ProjectSearch Diagnostic] Vector outer task AFTER JOIN for kNN 5-field on thread: {}", Thread.currentThread().getName());

            List<ScoredDocument> rewardScored = rewardFuture.join();
            List<ScoredDocument> titleScored = titleFuture.join();
            List<ScoredDocument> categoryVectorScored = catVecFuture.join();
            List<ScoredDocument> summaryScored = summaryFuture.join();
            List<ScoredDocument> descScored = descFuture.join();
            long knnElapsed = System.currentTimeMillis() - knnStart;

            Set<Long> categoryIntentBoostProjectIds = intentCategoryIds.isEmpty()
                    ? Set.of()
                    : resolveCategoryMemberProjectIds(intentCategoryIds);

            return new VectorBranchResult(
                    rewardScored, titleScored, categoryVectorScored, summaryScored, descScored,
                    categoryIntentBoostProjectIds, embeddingElapsed, knnElapsed
            );
        }, searchTaskExecutor).exceptionally(ex -> {
            log.warn("[ProjectSearch] Vector Branch 실행 중 예외 발생, BM25 단독 폴백: {}", ex.getMessage());
            return new VectorBranchResult(List.of(), List.of(), List.of(), List.of(), List.of(), Set.of(), 0, 0);
        });

        // ── 4. 두 Branch 결과 대기 및 결합 (Graceful Degradation) ─────────────────────
        log.debug("[ProjectSearch Diagnostic] Main thread BEFORE JOIN for BM25 and Vector Branch on thread: {}", Thread.currentThread().getName());
        CompletableFuture.allOf(bm25Future, vectorFuture).join();
        log.debug("[ProjectSearch Diagnostic] Main thread AFTER JOIN for BM25 and Vector Branch on thread: {}", Thread.currentThread().getName());

        List<ScoredDocument> keywordScored = bm25Future.join();
        VectorBranchResult vectorRes = vectorFuture.join();

        // Compatibility 평가용 QueryIntent 합류: 예산 내 완료되면 계절 충돌 제거 등에 사용, 초과 시 없이 완주.
        QueryIntent intent;
        try {
            intent = intentFuture.get(INTENT_JOIN_BUDGET_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.info("[ProjectSearch] QueryIntent가 예산({}ms) 내 미완료 → Compatibility 없이 진행. keyword='{}'", INTENT_JOIN_BUDGET_MS, trimmedKeyword);
            intent = QueryIntent.passThrough(trimmedKeyword);
        }
        long llmElapsed = System.currentTimeMillis() - llmStart;

        // Vector 결과가 전혀 없는 경우 (임베딩 실패, 모델 미설정 등) BM25 단독 반환
        if (vectorRes.rewardScored.isEmpty() && vectorRes.titleScored.isEmpty()
                && vectorRes.categoryVectorScored.isEmpty() && vectorRes.summaryScored.isEmpty()
                && vectorRes.descScored.isEmpty()) {
            long totalElapsed = System.currentTimeMillis() - totalStart;
            log.info("[ProjectSearch Latency Summary] 키워드='{}' (BM25 단독) | Total: {}ms",
                    trimmedKeyword, totalElapsed);
            return keywordScored.stream().map(ScoredDocument::projectId).toList();
        }

        // ── 5. Score-aware Hybrid Ranking + 2-Stage Compatibility Layer ─────────────
        long fusionStart = System.currentTimeMillis();
        List<Long> rankedProjectIds = fuseByScore(
                keywordScored,
                vectorRes.rewardScored,
                vectorRes.titleScored,
                vectorRes.categoryVectorScored,
                vectorRes.summaryScored,
                vectorRes.descScored,
                vectorRes.categoryIntentBoostProjectIds,
                intent
        );
        long fusionElapsed = System.currentTimeMillis() - fusionStart;
        long totalElapsed = System.currentTimeMillis() - totalStart;

        log.info("[ProjectSearch Latency Summary] 키워드='{}' | Total: {}ms | LLM(병렬): {}ms, Embedding: {}ms, kNN(5개병렬): {}ms, Fusion: {}ms | 결과: {}건",
                trimmedKeyword, totalElapsed, llmElapsed, vectorRes.embeddingElapsed, vectorRes.knnElapsed, fusionElapsed, rankedProjectIds.size());

        return rankedProjectIds;
    }

    private List<Long> resolveExactCategoryIds(String keyword) {
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<ProjectCategory> matched = categoryRepository.findByNameIgnoreCase(trimmed);
        if (matched.isEmpty()) {
            return List.of();
        }
        return withDescendants(matched);
    }

    private List<Long> withDescendants(List<ProjectCategory> matched) {
        Map<Long, List<Long>> childIdsByParentId = categoryRepository.findAll().stream()
                .filter(c -> c.getParentProjectCategoryId() != null)
                .collect(Collectors.groupingBy(ProjectCategory::getParentProjectCategoryId,
                        Collectors.mapping(ProjectCategory::getId, Collectors.toList())));

        List<Long> categoryIds = new ArrayList<>();
        Deque<Long> toVisit = new ArrayDeque<>(matched.stream().map(ProjectCategory::getId).toList());
        while (!toVisit.isEmpty()) {
            Long categoryId = toVisit.poll();
            categoryIds.add(categoryId);
            toVisit.addAll(childIdsByParentId.getOrDefault(categoryId, List.of()));
        }
        return categoryIds;
    }

    private Set<Long> resolveCategoryMemberProjectIds(List<Long> categoryIds) {
        Query query = Query.of(q -> q.terms(t -> t.field("categoryId")
                .terms(ts -> ts.value(categoryIds.stream().map(FieldValue::of).toList()))));
        NativeQuery nativeQuery = NativeQuery.builder().withQuery(query).withMaxResults(MAX_RESULTS).build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream()
                .map(hit -> hit.getContent() != null ? hit.getContent().projectId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<ScoredDocument> searchKeywordScored(Query keywordQuery) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(keywordQuery)
                .withMaxResults(MAX_RESULTS)
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream()
                .filter(hit -> hit.getContent() != null && hit.getContent().projectId() != null)
                .map(hit -> new ScoredDocument(
                        hit.getContent().projectId(),
                        hit.getScore() > 0 ? (double) hit.getScore() : 0.0))
                .toList();
    }

    private List<ScoredDocument> searchFieldKnnScored(String fieldName, List<Float> vectorList, int k, int numCandidates, List<Long> categoryIds) {
        SearchResponse<ProjectDocument> response;
        try {
            response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(k)
                            .knn(KnnSearch.of(knn -> {
                                knn.field(fieldName)
                                        .queryVector(vectorList)
                                        .k(k)
                                        .numCandidates(numCandidates)
                                        .similarity(MIN_KNN_SIMILARITY);
                                if (categoryIds != null && !categoryIds.isEmpty()) {
                                    knn.filter(Query.of(q -> q.terms(t -> t.field("categoryId")
                                            .terms(ts -> ts.value(categoryIds.stream().map(FieldValue::of).toList())))));
                                }
                                return knn;
                            })),
                    ProjectDocument.class
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (response == null || response.hits() == null || response.hits().hits() == null) {
            return List.of();
        }

        Map<Long, Double> bestScoreByProject = new HashMap<>();
        for (Hit<ProjectDocument> hit : response.hits().hits()) {
            if (hit.source() != null && hit.source().projectId() != null) {
                double esScore = hit.score() != null ? hit.score() : 0.0;
                double rawCosine = Math.max(0.0, 2.0 * esScore - 1.0);
                bestScoreByProject.merge(hit.source().projectId(), rawCosine, Math::max);
            }
        }

        return bestScoreByProject.entrySet().stream()
                .map(e -> new ScoredDocument(e.getKey(), e.getValue()))
                .toList();
    }

    private static final double BM25_SATURATION_K = 5.0;
    private static final double CATEGORY_INTENT_BOOST_WEIGHT = 0.10;

    /**
     * Score-aware Hybrid Ranking + 2-Stage Compatibility Layer:
     * 1) BM25 정규화 + 5개 Vector 필드 Raw Cosine 가중합 계산
     * 2) Candidate Set 대상 2-Stage Query-Product Compatibility 평가 (Requirements Relevance/Satisfaction/Conflict)
     * 3) Dynamic Cutoff 적용하여 노이즈 제거 후 최종 정렬
     */
    private List<Long> fuseByScore(
            List<ScoredDocument> keywordDocs,
            List<ScoredDocument> rewardDocs,
            List<ScoredDocument> titleDocs,
            List<ScoredDocument> categoryDocs,
            List<ScoredDocument> summaryDocs,
            List<ScoredDocument> descDocs,
            Set<Long> categoryIntentBoostProjectIds,
            QueryIntent intent) {

        List<ScoredDocument> normKeyword = ScoredDocument.normalizeBm25(keywordDocs, BM25_SATURATION_K);
        List<ScoredDocument> normTitle = ScoredDocument.asDirectVectorScores(titleDocs);
        List<ScoredDocument> normReward = ScoredDocument.asDirectVectorScores(rewardDocs);
        List<ScoredDocument> normCat = ScoredDocument.asDirectVectorScores(categoryDocs);
        List<ScoredDocument> normSummary = ScoredDocument.asDirectVectorScores(summaryDocs);
        List<ScoredDocument> normDesc = ScoredDocument.asDirectVectorScores(descDocs);

        Map<Long, Double> finalScores = new HashMap<>();
        Map<Long, Map<String, Double>> breakdown = new HashMap<>();

        accumulateScores(finalScores, breakdown, normKeyword, BM25_WEIGHT, "BM25");
        accumulateScores(finalScores, breakdown, normTitle, TITLE_VECTOR_WEIGHT, "title");
        accumulateScores(finalScores, breakdown, normReward, REWARD_VECTOR_WEIGHT, "reward");
        accumulateScores(finalScores, breakdown, normCat, CATEGORY_VECTOR_WEIGHT, "category");
        accumulateScores(finalScores, breakdown, normSummary, SUMMARY_VECTOR_WEIGHT, "summary");
        accumulateScores(finalScores, breakdown, normDesc, DESCRIPTION_VECTOR_WEIGHT, "desc");

        if (finalScores.isEmpty()) {
            return List.of();
        }

        // ── 2-Stage Compatibility Layer 평가 (후보군 사전 탈락 방지) ───
        if (intent != null && intent.hasRequirements()) {
            List<Long> candidateIds = new ArrayList<>(finalScores.keySet());
            Map<Long, ProjectDocument> docMap = fetchDocumentsByIds(candidateIds);

            for (Long pid : candidateIds) {
                ProjectDocument doc = docMap.get(pid);
                if (doc != null) {
                    QueryProductCompatibilityEvaluator.CompatibilityResult comp = compatibilityEvaluator.evaluate(doc, intent);

                    if (comp.isStrictConflict()) {
                        log.info("[ProjectSearch] Strict Conflict로 후보 제외: projectId={}, title='{}', sat={}, conf={}, reason='{}'",
                                pid, doc.title(), comp.satisfactionScore(), comp.conflictScore(), comp.reason());
                        finalScores.remove(pid);
                        breakdown.remove(pid);
                        continue;
                    }

                    if (comp.totalAdjustment() != 0.0) {
                        finalScores.merge(pid, comp.totalAdjustment(), Double::sum);
                        breakdown.computeIfAbsent(pid, k -> new HashMap<>())
                                .put("compatibilityAdj", comp.totalAdjustment());
                    }
                }
            }
        }

        if (finalScores.isEmpty()) {
            log.info("[ProjectSearch] Strict Requirement Conflict로 모든 후보가 제외되어 0건 반환 (정상 결과)");
            return List.of();
        }

        // 동적 컷오프 계산 (최고 점수 대비 35% 미만 노이즈 차단)
        double maxScore = finalScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double dynamicCutoff = Math.max(MIN_FINAL_SCORE, maxScore * RELATIVE_SCORE_CUTOFF_RATIO);

        Set<Long> explicitKeywordSet = new HashSet<>();
        for (ScoredDocument d : keywordDocs) {
            explicitKeywordSet.add(d.projectId());
        }

        Set<Long> survivors = finalScores.entrySet().stream()
                .filter(entry -> explicitKeywordSet.contains(entry.getKey()) || entry.getValue() >= dynamicCutoff)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Category Intent 소프트 부스트
        for (Long projectId : categoryIntentBoostProjectIds) {
            if (survivors.contains(projectId)) {
                finalScores.merge(projectId, CATEGORY_INTENT_BOOST_WEIGHT, Double::sum);
                breakdown.computeIfAbsent(projectId, k -> new HashMap<>())
                        .put("categoryIntentBoost", CATEGORY_INTENT_BOOST_WEIGHT);
            }
        }

        Comparator<Map.Entry<Long, Double>> comparator = Map.Entry.<Long, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry.<Long, Double>comparingByKey().reversed());

        List<Map.Entry<Long, Double>> ranked = finalScores.entrySet().stream()
                .filter(entry -> survivors.contains(entry.getKey()))
                .sorted(comparator)
                .limit(MAX_RESULTS)
                .toList();

        logTop5Results(ranked, breakdown);

        return ranked.stream()
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<Long, ProjectDocument> fetchDocumentsByIds(List<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        Query query = Query.of(q -> q.terms(t -> t.field("projectId")
                .terms(ts -> ts.value(projectIds.stream().map(FieldValue::of).toList()))));
        NativeQuery nativeQuery = NativeQuery.builder().withQuery(query).withMaxResults(projectIds.size()).build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);

        Map<Long, ProjectDocument> map = new HashMap<>();
        for (var hit : hits) {
            if (hit.getContent() != null && hit.getContent().projectId() != null) {
                map.put(hit.getContent().projectId(), hit.getContent());
            }
        }
        return map;
    }

    private void accumulateScores(Map<Long, Double> finalScores, Map<Long, Map<String, Double>> breakdown,
                                  List<ScoredDocument> docs, double weight, String fieldName) {
        for (ScoredDocument doc : docs) {
            double contribution = doc.normalizedScore() * weight;
            finalScores.merge(doc.projectId(), contribution, Double::sum);
            breakdown.computeIfAbsent(doc.projectId(), k -> new HashMap<>()).put(fieldName, contribution);
        }
    }

    private void logTop5Results(List<Map.Entry<Long, Double>> ranked, Map<Long, Map<String, Double>> breakdown) {
        if (ranked.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("\n[HybridRanking] Top-5 랭킹 결과 및 Score Breakdown:\n");
        int limit = Math.min(5, ranked.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<Long, Double> entry = ranked.get(i);
            Long pid = entry.getKey();
            Double total = entry.getValue();
            Map<String, Double> parts = breakdown.getOrDefault(pid, Map.of());
            String details = parts.entrySet().stream()
                    .map(e -> String.format("%s=%.3f", e.getKey(), e.getValue()))
                    .collect(Collectors.joining(", "));
            sb.append(String.format("  #%d [projectId=%d] Total Score=%.4f | Breakdown: {%s}\n", i + 1, pid, total, details));
        }
        log.info(sb.toString());
    }

    private List<Long> searchFallback(String keyword, Throwable cause) {
        log.warn("프로젝트 검색 호출 실패, DB LIKE 검색으로 대체합니다. 원인: {}", cause.toString());
        return projectRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(escapeLikeWildcards(keyword)).stream()
                .map(Project::getProjectId)
                .toList();
    }

    private String escapeLikeWildcards(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public void bulkIndex(List<Project> projects) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_BULK_INDEX_ID).run(
                () -> {
                    doBulkIndex(projects);
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 벌크 색인 실패. 대상 개수={}", projects.size(), cause);
                    return null;
                });
    }

    private void doBulkIndex(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<Long> projectIds = projects.stream().map(Project::getProjectId).toList();
        Map<Long, List<String>> rewardNamesByProject = rewardRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(Reward::getProjectId, Collectors.mapping(Reward::getName, Collectors.toList())));
        Map<Long, ProjectCategory> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ProjectCategory::getId, c -> c, (a, b) -> a));

        List<ProjectEmbeddingService.ProjectEmbeddingTarget> targets = new ArrayList<>();
        for (Project project : projects) {
            List<String> rewards = rewardNamesByProject.getOrDefault(project.getProjectId(), List.of());
            String categoryHierarchy = resolveCategoryHierarchy(project.getCategoryId(), categoryMap);
            targets.add(new ProjectEmbeddingService.ProjectEmbeddingTarget(project, categoryHierarchy, rewards));
        }

        Map<Long, ProjectFieldVectors> vectorsByProject = embeddingService.generateFieldVectorsBulk(targets);

        List<ProjectDocument> documents = new ArrayList<>();
        for (Project project : projects) {
            List<String> rewards = rewardNamesByProject.getOrDefault(project.getProjectId(), List.of());
            ProjectFieldVectors vectors = vectorsByProject.getOrDefault(project.getProjectId(), ProjectFieldVectors.empty());
            documents.add(toDocument(project, rewards, vectors));
        }

        elasticsearchOperations.save(documents);
    }

    private String resolveCategoryHierarchy(Long categoryId) {
        if (categoryId == null) {
            return "";
        }
        Map<Long, ProjectCategory> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ProjectCategory::getId, c -> c, (a, b) -> a));
        return resolveCategoryHierarchy(categoryId, categoryMap);
    }

    private String resolveCategoryHierarchy(Long categoryId, Map<Long, ProjectCategory> categoryMap) {
        if (categoryId == null) {
            return "";
        }
        ProjectCategory category = categoryMap.get(categoryId);
        if (category == null) {
            return "";
        }
        if (category.getParentProjectCategoryId() != null) {
            ProjectCategory parent = categoryMap.get(category.getParentProjectCategoryId());
            if (parent != null) {
                return parent.getName() + " > " + category.getName();
            }
        }
        return category.getName();
    }

    private List<ProjectSuggestion> doAutocomplete(String prefix) {
        List<String> words = Arrays.stream(prefix.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }

        Query query = Query.of(q -> q.bool(b -> {
            words.forEach(word -> b.must(m -> m.prefix(p -> p.field("title").value(word).caseInsensitive(true))));
            return b;
        }));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(AUTOCOMPLETE_CANDIDATE_LIMIT)
                .build();

        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream()
                .map(hit -> hit.getContent())
                .filter(doc -> doc != null && doc.projectId() != null && doc.title() != null)
                .map(doc -> new ProjectSuggestion(doc.projectId(), doc.title()))
                .toList();
    }

    private List<ProjectSuggestion> autocompleteFallback(Throwable cause) {
        log.warn("자동완성 호출 실패, 빈 목록으로 폴백합니다. 원인: {}", cause.toString());
        return List.of();
    }
}
