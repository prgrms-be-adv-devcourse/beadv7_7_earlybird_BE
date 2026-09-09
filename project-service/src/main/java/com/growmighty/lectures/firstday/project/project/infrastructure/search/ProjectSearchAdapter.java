package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import com.growmighty.lectures.firstday.project.category.domain.CategoryHierarchy;
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
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
 * Nori 형태소 분석기 기반 BM25 키워드 검색을 Score-aware Hybrid Fusion으로 결합해 <b>후보 상위 40개</b>를
 * 뽑고, 이를 {@link Reranker}(Cohere Rerank)로 최종 재정렬한다. 명백한 속성 충돌(계절·반려동물 종·성별) 상품은
 * {@link AttributeConflictFilter}로 rerank 전에 하드 제외한다.
 *
 * <p>동의어 확장은 ES가 한다 — title/summary/description/rewardNames 넷 다 search_analyzer가
 * {@code korean_search}이고, 그 안의 {@code korean_synonym_graph}가 synonym.txt를 읽어 검색 시점에
 * 토큰을 확장한다(project-index-settings.json). 그래서 여기서는 사용자 원본 쿼리를 그대로 넘긴다.
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
    /** fusion 후 리랭커에 넘길 상위 후보 수. */
    private static final int RERANK_CANDIDATE_LIMIT = 40;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;
    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository categoryRepository;
    private final RewardRepository rewardRepository;
    private final CategoryIntentResolver categoryIntentResolver;
    private final Reranker reranker;
    private final AttributeConflictFilter attributeConflictFilter;
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

    private List<Long> doSearch(String keyword) {
        long totalStart = System.currentTimeMillis();
        String trimmedKeyword = keyword.trim();

        // ── 1. 전처리 (초고속 인메모리) ─────────────────────────────────────────────
        //   · 정확한 카테고리명 → kNN 하드 스코프
        //   동의어 확장은 여기서 안 한다 — ES search_analyzer(korean_synonym_graph)가 검색 시점에 처리한다.
        List<Long> exactCategoryIds = resolveExactCategoryIds(trimmedKeyword);

        if (!exactCategoryIds.isEmpty()) {
            log.info("[ProjectSearch] 키워드='{}' → 카테고리명 일치로 kNN 하드 스코프 적용: categoryIds={}", trimmedKeyword, exactCategoryIds);
        }

        // ── 2. [Branch 1: BM25] ────────────────────────────────────────
        CompletableFuture<List<ScoredDocument>> bm25Future = CompletableFuture.supplyAsync(() -> {
            Query keywordQuery = Query.of(q -> q.bool(b -> {
                b.should(s -> s.match(m -> m.field("title").query(trimmedKeyword).boost(2.0f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("summary").query(trimmedKeyword).boost(1.2f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        .should(s -> s.match(m -> m.field("description").query(trimmedKeyword).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                        // 다른 셋과 같은 minimumShouldMatch를 건다 — 이게 빠져 있으면 동의어로 확장된
                        // 토큰 하나가 리워드명에 스치기만 해도 통과한다. 운영 실측(#755)에서 "요리용 책"
                        // 결과 7건이 title/summary/description 전부 0건인데 여기로만 들어온 시집이었다.
                        .should(s -> s.match(m -> m.field("rewardNames").query(trimmedKeyword).boost(1.5f)
                                .minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)));

                if (!exactCategoryIds.isEmpty()) {
                    b.should(s -> s.matchAll(m -> m));
                    b.filter(f -> f.terms(t -> t.field("categoryId")
                            .terms(ts -> ts.value(exactCategoryIds.stream().map(FieldValue::of).toList()))));
                }
                return b.minimumShouldMatch("1");
            }));
            return searchKeywordScored(keywordQuery);
        }, searchTaskExecutor);

        // ── 3. [Branch 2: Embedding → 5개 필드 kNN 병렬] ──────────────────
        record VectorBranchResult(
                List<ScoredDocument> rewardScored,
                List<ScoredDocument> titleScored,
                List<ScoredDocument> categoryVectorScored,
                List<ScoredDocument> summaryScored,
                List<ScoredDocument> descScored,
                Set<Long> categoryIntentBoostProjectIds
        ) {}

        CompletableFuture<VectorBranchResult> vectorFuture = CompletableFuture.supplyAsync(() -> {
            float[] queryVector = null;
            try {
                queryVector = embeddingService.generateEmbedding(trimmedKeyword);
            } catch (Exception e) {
                log.warn("[ProjectSearch] 임베딩 생성 실패: {}", e.getMessage());
            }
            if (queryVector == null || queryVector.length == 0) {
                return new VectorBranchResult(List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
            }

            List<Long> intentCategoryIds = exactCategoryIds.isEmpty()
                    ? categoryIntentResolver.resolveCategoryIntent(queryVector)
                    : List.of();
            if (exactCategoryIds.isEmpty()) {
                log.info("[ProjectSearch] 키워드='{}' → 카테고리 의도: {}", trimmedKeyword,
                        intentCategoryIds.isEmpty() ? "미적용(전체 검색)" : "소프트 부스트 " + intentCategoryIds);
            }

            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float f : queryVector) {
                vectorList.add(f);
            }

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
            CompletableFuture.allOf(rewardFuture, titleFuture, catVecFuture, summaryFuture, descFuture).join();

            Set<Long> categoryIntentBoostProjectIds = intentCategoryIds.isEmpty()
                    ? Set.of()
                    : resolveCategoryMemberProjectIds(intentCategoryIds);

            return new VectorBranchResult(
                    rewardFuture.join(), titleFuture.join(), catVecFuture.join(),
                    summaryFuture.join(), descFuture.join(), categoryIntentBoostProjectIds);
        }, searchTaskExecutor).exceptionally(ex -> {
            log.warn("[ProjectSearch] Vector Branch 실행 중 예외, BM25 단독 폴백: {}", ex.getMessage());
            return new VectorBranchResult(List.of(), List.of(), List.of(), List.of(), List.of(), Set.of());
        });

        CompletableFuture.allOf(bm25Future, vectorFuture).join();
        List<ScoredDocument> keywordScored = bm25Future.join();
        VectorBranchResult vectorRes = vectorFuture.join();

        // Vector 결과가 전혀 없으면 (임베딩 실패 등) BM25 단독 반환
        if (vectorRes.rewardScored.isEmpty() && vectorRes.titleScored.isEmpty()
                && vectorRes.categoryVectorScored.isEmpty() && vectorRes.summaryScored.isEmpty()
                && vectorRes.descScored.isEmpty()) {
            log.info("[ProjectSearch] 키워드='{}' (BM25 단독) | Total: {}ms",
                    trimmedKeyword, System.currentTimeMillis() - totalStart);
            return keywordScored.stream().map(ScoredDocument::projectId).toList();
        }

        // ── 4. fusion → 후보 상위 40 (candidate generation, 최종 순위 아님) ─────────
        List<Long> candidates = fuseByScore(
                keywordScored,
                vectorRes.rewardScored, vectorRes.titleScored, vectorRes.categoryVectorScored,
                vectorRes.summaryScored, vectorRes.descScored,
                vectorRes.categoryIntentBoostProjectIds
        ).stream().limit(RERANK_CANDIDATE_LIMIT).toList();

        // ── 5. 속성 충돌 하드 제외 → 문서 텍스트 1회 조회 → 리랭커(원본 쿼리) ─────────────
        //   이 단계가 실패해도 검색이 멈추면 안 된다 — fusion 순서(candidates)로 완주한다.
        List<Long> finalIds = candidates;
        int afterFilterSize = candidates.size();
        try {
            Map<Long, ProjectDocument> docs = fetchDocumentsByIds(candidates);
            List<Long> afterFilter = attributeConflictFilter.filter(trimmedKeyword, candidates, docs);
            afterFilterSize = afterFilter.size();
            finalIds = reranker.rerank(trimmedKeyword, afterFilter, docs);
        } catch (Exception e) {
            log.warn("[ProjectSearch] rerank 단계 실패 → fusion 순서로 완주. 원인: {}", e.toString());
        }

        log.info("[ProjectSearch Latency Summary] 키워드='{}' | Total: {}ms | 후보: {}, 충돌필터후: {}, 최종: {}",
                trimmedKeyword, System.currentTimeMillis() - totalStart, candidates.size(), afterFilterSize, finalIds.size());
        return finalIds;
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
        return CategoryHierarchy.of(categoryRepository.findAll())
                .withDescendants(matched.stream().map(ProjectCategory::getId).toList());
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
     * Score-aware Hybrid Ranking — <b>후보 생성 전용</b>. 최종 순위는 {@link Reranker}가 낸다.
     * 1) BM25 포화 정규화 + 5개 Vector 필드 Raw Cosine 가중합
     * 2) 동적 컷오프(최고 점수 대비 35% 미만)로 노이즈 제거 + Category Intent 소프트 부스트
     * 3) 스코어 내림차순 정렬한 projectId 목록 반환 (호출부에서 상위 N개만 취해 리랭커로)
     */
    private List<Long> fuseByScore(
            List<ScoredDocument> keywordDocs,
            List<ScoredDocument> rewardDocs,
            List<ScoredDocument> titleDocs,
            List<ScoredDocument> categoryDocs,
            List<ScoredDocument> summaryDocs,
            List<ScoredDocument> descDocs,
            Set<Long> categoryIntentBoostProjectIds) {

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
        hits.stream()
                .map(SearchHit::getContent)
                .filter(c -> c != null && c.projectId() != null)
                .forEach(c -> map.put(c.projectId(), c));
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
        CategoryHierarchy categoryHierarchies = CategoryHierarchy.of(categoryRepository.findAll());

        List<ProjectEmbeddingService.ProjectEmbeddingTarget> targets = new ArrayList<>();
        for (Project project : projects) {
            List<String> rewards = rewardNamesByProject.getOrDefault(project.getProjectId(), List.of());
            String categoryHierarchy = categoryHierarchies.path(project.getCategoryId());
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

    /**
     * categoryVector 임베딩용 계층 문자열. 루트까지 조상 전체를 붙인다 —
     * 부모 1단계만 넣으면 리프 프로젝트가 상위 카테고리 의미를 잃는다(#765).
     * query-side {@link CategoryIntentResolver}와 같은 표현을 써야 벡터가 맞물린다.
     */
    private String resolveCategoryHierarchy(Long categoryId) {
        return CategoryHierarchy.of(categoryRepository.findAll()).path(categoryId);
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
