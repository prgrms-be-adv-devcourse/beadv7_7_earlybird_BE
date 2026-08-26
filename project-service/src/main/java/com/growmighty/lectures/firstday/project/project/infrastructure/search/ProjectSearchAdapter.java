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
import java.util.stream.Collectors;

/**
 * Elasticsearch 검색 포트 구현체.
 * 5개 독립 벡터(title, summary, description, category, reward)의 코사인 점수 보존 kNN 검색과
 * Nori 형태소 분석기 기반 BM25 키워드 검색을 Score-aware Hybrid Fusion으로 결합하여 랭킹을 산출한다.
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

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;
    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository categoryRepository;
    private final RewardRepository rewardRepository;
    private final CategoryIntentResolver categoryIntentResolver;

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
        String trimmedKeyword = keyword.trim();
        float[] queryVector = embeddingService.generateEmbedding(trimmedKeyword);
        
        // 1. 정확한 카테고리명 매칭 확인
        List<Long> exactCategoryIds = resolveExactCategoryIds(trimmedKeyword);
        
        // 2. 카테고리 의도(Category Intent) 추론 (정확 매칭이 없을 때만 질의 벡터와 카테고리 임베딩 직접 비교)
        List<Long> intentCategoryIds = exactCategoryIds.isEmpty() && queryVector != null
                ? categoryIntentResolver.resolveCategoryIntent(queryVector)
                : List.of();

        // kNN 하드 스코프는 정확한 카테고리명 매칭일 때만 적용한다.
        // Intent 추론은 임베딩 유사도 기반 추정이라 오탐 시(예: "간식"->"상의") 하드 필터로 걸면
        // 진짜 정답이 후보군에서 완전히 사라져 복구 불가능하므로, 소프트 부스트로만 반영한다.
        if (!exactCategoryIds.isEmpty()) {
            log.info("[ProjectSearch] 키워드='{}' -> 카테고리명 일치로 kNN 하드 스코프 적용: categoryIds={}", trimmedKeyword, exactCategoryIds);
        } else if (!intentCategoryIds.isEmpty()) {
            log.info("[ProjectSearch] 키워드='{}' -> 카테고리 의도 소프트 부스트 적용: categoryIds={}", trimmedKeyword, intentCategoryIds);
        } else {
            log.info("[ProjectSearch] 키워드='{}' -> 카테고리 스코프 미적용 (전체 검색)", trimmedKeyword);
        }

        List<String> slangSynonyms = resolveSlangSynonyms(trimmedKeyword);

        // 3. BM25 키워드 쿼리 (정확 매칭 시에만 BM25 categoryId filter 적용)
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

        List<ScoredDocument> keywordScored = searchKeywordScored(keywordQuery);
        if (queryVector == null || queryVector.length == 0) {
            return keywordScored.stream().map(ScoredDocument::projectId).toList();
        }

        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float f : queryVector) {
            vectorList.add(f);
        }

        // 4. 5개 필드 kNN 검색 (Cosine 점수 보존 및 ES score -> raw cosine 역변환)
        List<ScoredDocument> rewardScored = searchFieldKnnScored("rewardVector", vectorList, 20, 50, exactCategoryIds);
        List<ScoredDocument> titleScored = searchFieldKnnScored("titleVector", vectorList, 20, 50, exactCategoryIds);
        List<ScoredDocument> categoryVectorScored = searchFieldKnnScored("categoryVector", vectorList, 20, 50, exactCategoryIds);
        List<ScoredDocument> summaryScored = searchFieldKnnScored("summaryVector", vectorList, 20, 50, exactCategoryIds);
        List<ScoredDocument> descScored = searchFieldKnnScored("descriptionVector", vectorList, 20, 50, exactCategoryIds);

        // Category Intent(오탐 가능한 임베딩 추정치)는 후보군을 배제하지 않고, 이미 후보에 오른
        // 문서 중 해당 카테고리에 속한 것만 소프트 부스트한다.
        Set<Long> categoryIntentBoostProjectIds = intentCategoryIds.isEmpty()
                ? Set.of()
                : resolveCategoryMemberProjectIds(intentCategoryIds);

        // 5. Score-aware Hybrid Ranking
        return fuseByScore(keywordScored, rewardScored, titleScored, categoryVectorScored, summaryScored, descScored,
                categoryIntentBoostProjectIds);
    }

    /**
     * Exact Category Match는 "검색어 전체가 카테고리명과 완전히 같을 때"만 성립한다 — 즉 사용자가
     * 카테고리를 직접 선택한 것과 동일하게 볼 수 있는 경우다. "반려동물 음식"처럼 카테고리명 뒤에
     * 다른 말이 붙은 복합 검색어는 여기 해당하지 않고(카테고리+검색의도), CategoryIntentResolver의
     * 소프트 부스트 경로로만 반영된다. 접두어 매칭은 하드 스코프의 오탐 위험이 커서 쓰지 않는다.
     */
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

    /** 주어진 카테고리(들)에 속한 프로젝트 ID 집합을 조회한다. Category Intent 소프트 부스트 대상 판별용. */
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

    /**
     * 특정 dense_vector 필드에 대한 단일 kNN 검색.
     * Elasticsearch의 cosine similarity kNN 반환 score: _score = (1 + cosine) / 2
     * 이를 raw cosine = max(0.0, 2 * _score - 1) 로 역변환하여 보존한다.
     */
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
                // ES cosine _score = (1 + cosine) / 2 -> raw cosine 복원 (음수는 0으로 clamp)
                double rawCosine = Math.max(0.0, 2.0 * esScore - 1.0);
                bestScoreByProject.merge(hit.source().projectId(), rawCosine, Math::max);
            }
        }

        return bestScoreByProject.entrySet().stream()
                .map(e -> new ScoredDocument(e.getKey(), e.getValue()))
                .toList();
    }

    /** BM25 Saturation 상수 (단일 토큰 어휘 일치의 급격한 포화 방지) */
    private static final double BM25_SATURATION_K = 5.0;
    /** Category Intent(오탐 가능) 소프트 부스트 가중치 — 이미 후보에 오른 문서만 대상으로 가산 */
    private static final double CATEGORY_INTENT_BOOST_WEIGHT = 0.10;

    /**
     * Score-aware Hybrid Ranking:
     * 1) BM25 점수는 포화 함수 score / (score + K)로 정규화하여 극단적인 점수 증폭 방지
     * 2) 5개 Vector 필드의 Cosine 점수는 Min-Max 왜곡 없이 Raw Cosine [0.0, 1.0]을 직접 보존
     * 3) Category Intent가 지목한 카테고리에 속한 후보는 소프트 부스트 가산(신규 후보 생성 아님)
     * 4) 가중합 계산 후 동적 컷오프로 노이즈 제거
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

        // 컷오프는 카테고리 부스트를 더하기 전, 순수 텍스트/의미 관련도 점수만으로 판단한다.
        // 부스트를 먼저 더해버리면 "카테고리만 맞고 내용은 무관한" 후보(예: '반려동물 음식'
        // 검색에 캣타워·산책줄)도 부스트값만으로 컷오프를 넘어 결과에 섞여 들어간다.
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

        // Category Intent 소프트 부스트: 컷오프를 이미 통과한 후보의 재랭킹에만 사용한다.
        // 컷오프를 못 넘은 후보를 부스트만으로 되살리지 않는다(오탐 카테고리로 무관한 상품이
        // 결과에 끼어드는 것을 방지).
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

        // 50개 프로젝트의 250개 필드 벡터를 단 1회의 OpenAI Batch API 호출로 일괄 생성
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
