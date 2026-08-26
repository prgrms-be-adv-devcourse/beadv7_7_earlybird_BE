package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * index()/remove()는 ES를 직접 부르지 않고 이벤트만 발행한다.
 * 사전 계산된 임베딩(Project.getEmbedding())을 사용하여 ES에 색인하며,
 * 검색 시 Nori 형태소 분석기 키워드 매칭과 kNN 벡터 하이브리드 검색을 함께 실행한다.
 *
 * <p>autocomplete()는 title에 대해 공백으로 나눈 각 단어를 개별 prefix 쿼리로 만들고 전부 AND로
 * 묶는다 — title은 nori로 분석되어 형태소 토큰으로 쪼개지므로, 검색어를 통째로 하나의 prefix로 물으면
 * 토큰과 정확히 일치하지 않는 한(공백 포함 검색어는 애초에 매치될 수 없음) 매치가 안 된다. 단어별로
 * "제목의 어떤 토큰이 이 단어로 시작하는가"를 각각 물어 AND로 묶으면, 순서/위치와 무관하게 제목 안
 * 어디에 있는 단어든 매치되면서(예: "밥"이 "고양이 밥 주는 기계"에 매치) 여러 단어를 넣을수록 결과가
 * 점점 좁혀지는(모든 단어가 각자 어떤 토큰이든 prefix 매치해야 함) 동작을 얻는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private static final String INDEX_NAME = "projects";
    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
    private static final int MAX_RESULTS = 200;
    /**
     * ES의 rrf retriever는 Basic 라이선스에선 호출 자체가 거부된다(Enterprise 전용 기능 게이트,
     * self-generated trial 라이선스는 30일 후 만료) — 그래서 키워드/kNN을 각각 별도로 호출하고
     * RRF 공식(Σ 1/(rankConstant+rank))을 애플리케이션 코드에서 직접 계산해 합친다(fuseByRank).
     */
    private static final int RRF_RANK_CONSTANT = 60;
    /** 키워드 일치(BM25/동의어) RRF 가중치: 명시적 키워드 일치 결과에 높은 우선순위 부여 */
    private static final double KEYWORD_RRF_WEIGHT = 1.0;
    /** kNN 의미 벡터 RRF 가중치: 문맥/유사도 기반 보조 추천 가중치 */
    private static final double KNN_RRF_WEIGHT = 0.8;
    /**
     * text-embedding-3-small 모델 기준 의미 유사도 하한 — hit.score()와 같은 정규화 스케일(코사인
     * (-1~1)을 (1+cosine)/2로 정규화한 0~1 범위) 기준값이다. ES kNN 쿼리의 `similarity`
     * 파라미터는 이 정규화된 hit 점수가 아니라 원본 코사인(-1~1) 값을 기준으로 컷하므로,
     * searchKnnIds에서 `2 * threshold - 1`로 변환해서 넘긴다(이전엔 이 상수를 변환 없이 그대로
     * 넘겨 실제로는 항상 이 값이 곧 원본 코사인 컷오프였다 — 단위 버그였지만 결과적으로 이 도메인엔
     * 맞는 값이었다, 아래 참고).
     *
     * <p>값 0.675: "물고기"→"연어 동결건조 간식"(정규화 0.61, 실제로 관련 있음)과 "영화"→
     * "OO 에세이집"류(정규화 0.58~0.622, 전부 무관함)의 실측 점수 분포가 서로 겹쳐서, 이 모델·
     * 이 데이터셋(비슷한 톤의 짧은 한국어 홍보문구)에서는 flat threshold로 진짜/가짜를 깔끔히
     * 못 가른다. 0.35로 낮추면(정규화 관점에서 "제대로 된" 값) recall은 늘지만 "영화"에 에세이집이
     * 대거 섞이는 등 노이즈가 함께 늘어 실측 회귀가 깨짐 — 그래서 이전부터 검증되어온 실효 컷오프
     * (0.675)를 그대로 유지한다. "물고기→연어"처럼 놓치는 케이스는 이 threshold 자체보다
     * 임베딩 입력(현재 title+summary+description만 사용, category/rewardNames 미포함)을
     * 확장해서 신호 자체를 넓히는 쪽으로 풀어야 한다 — 별도 재임베딩 작업, 이번 범위 밖.
     */
    private static final float KNN_SIMILARITY_THRESHOLD = 0.675f;
    /** RRF 스코어 하한: 가중치 RRF 결합 점수 기준 하위 노이즈 문서 필터링 */
    private static final double RRF_MIN_SCORE = 0.005;
    /** ES 후보 과다조회 한도 — 최종 10개 컷은 ProjectServiceImpl이 MySQL 가시성 필터링 후 수행한다. */
    private static final int AUTOCOMPLETE_CANDIDATE_LIMIT = 50;
    /**
     * 검색어 토큰이 2개 이하면 전부, 3개 이상이면 70%를 일치시켜야 match clause가 통과한다 — nori가
     * 사전에 없는 속어를 음절/형태소 단위로 쪼갤 때(예: "자동차"→"자동"+"차") 그중 흔한 한 조각만
     * 겹쳐도 매치되는 것을 막는다. synonym filter가 같은 position에 만드는 동의어들은 ES가 하나의
     * clause로 묶어 처리하므로 이 값과 충돌하지 않는다(동일 position 확장은 clause 수를 안 늘림).
     */
    private static final String MATCH_MINIMUM_SHOULD_MATCH = "2<70%";

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;
    private final ProjectRepository projectRepository;
    private final ProjectEmbeddingPersister embeddingPersister;
    private final ProjectCategoryRepository categoryRepository;
    private final RewardRepository rewardRepository;

    @Override
    public void index(Project project) {
        eventPublisher.publishEvent(new ProjectIndexRequestedEvent(project.getProjectId()));
    }

    @Override
    public void remove(Long projectId) {
        eventPublisher.publishEvent(new ProjectRemovedFromIndexEvent(projectId));
    }

    /**
     * 실제 색인 실행 — ProjectSearchIndexEventListener가 트랜잭션 커밋 후 DB에서 프로젝트를 다시
     * 조회해 최신 상태를 넘겨준다. DB에 저장된 사전 생성 임베딩(project.getEmbedding())을 사용한다.
     */
    void applyIndex(Project project) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> {
                    List<String> rewardNames = rewardRepository.findByProjectId(project.getProjectId()).stream()
                            .map(Reward::getName)
                            .toList();
                    elasticsearchOperations.save(toDocument(project, rewardNames));
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), cause);
                    return null;
                });
    }

    private ProjectDocument toDocument(Project project, List<String> rewardNames) {
        float[] embedding = project.getEmbedding();
        if (embedding != null && embedding.length == 0) {
            embedding = null;
        }
        return new ProjectDocument(project.getProjectId(), project.getTitle(), project.getSummary(),
                project.getDescription(), project.getCategoryId(), rewardNames, embedding);
    }

    /** 실제 삭제 실행 — ProjectSearchIndexEventListener가 트랜잭션 커밋 후에만 호출한다. */
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
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> doSearch(keyword),
                cause -> searchFallback(keyword, cause));
    }

    @Override
    public List<ProjectSuggestion> autocomplete(String prefix) {
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_AUTOCOMPLETE_ID).run(
                () -> doAutocomplete(prefix),
                this::autocompleteFallback);
    }

    private List<Long> doSearch(String keyword) {
        float[] queryVector = embeddingService.generateEmbedding(keyword);
        List<Long> categoryIds = resolveCategoryIds(keyword);
        Query keywordQuery = Query.of(q -> q.bool(b -> {
            b.should(s -> s.match(m -> m.field("title").query(keyword).boost(2.0f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                    .should(s -> s.match(m -> m.field("summary").query(keyword).boost(1.2f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                    .should(s -> s.match(m -> m.field("description").query(keyword).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                    .should(s -> s.match(m -> m.field("rewardNames").query(keyword).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)));
            if (!categoryIds.isEmpty()) {
                // 카테고리명과 정확히 일치한 고신뢰 신호이므로 should(가산점)가 아니라 filter(강제
                // 제약)로 건다 — "책" 검색 시 description에 "책"이라는 단어가 우연히 섞인 무관한
                // 카테고리 상품(예: 커피머신)이 텍스트 should만으로 후보에 들어오는 걸 막는다.
                // 이때는 minimumShouldMatch를 강제하지 않는다 — filter만으로 그 카테고리(+하위)
                // 상품 전부가 후보가 되고, 위 텍스트 should들은 그중 순위를 매기는 가산점으로만
                // 남는다. "반려동물"처럼 상위 카테고리로 검색하면, 자기 설명에 "반려동물"이라는
                // 단어가 literal하게 없는 하위 카테고리 상품(캣타워, 급식기 등)도 전부 나와야 한다.
                return b.filter(f -> f.terms(t -> t.field("categoryId")
                        .terms(ts -> ts.value(categoryIds.stream().map(FieldValue::of).toList()))));
            }
            // bool에 should만 있을 때의 ES 기본값(should 중 최소 1개 매치)에 암묵적으로 기대지 않고
            // 명시한다 — 카테고리 필터가 없는 일반 텍스트 검색에서는 여전히 텍스트 매치가 필수다.
            return b.minimumShouldMatch("1");
        }));

        List<Long> keywordIds = searchKeywordIds(keywordQuery);
        if (queryVector == null || queryVector.length == 0) {
            return keywordIds;
        }
        List<Long> knnIds = searchKnnIds(queryVector);
        return fuseByRank(keywordIds, knnIds);
    }

    /**
     * 검색어가 실제 카테고리명과 정확히(공백 트림, 대소문자 무시) 일치할 때만 그 카테고리 id를
     * 반환한다. 비어있지 않으면 doSearch가 이를 filter(강제 제약)로 걸어 "카테고리명과 정확히
     * 일치하는 검색어인데, 텍스트만 우연히 겹치는 다른 카테고리 상품"이 후보에서 빠지게 한다
     * (강사 피드백 — 예: "책" 검색 시 description에 "책"이 섞인 커피머신 노출). "여행"처럼
     * 카테고리명이 아닌 일반 단어는 여기서 걸리는 게 없어(빈 리스트) filter가 안 걸리고 기존
     * 텍스트/kNN 매치만 그대로 동작하며, 서로 다른 카테고리에 걸쳐 있는 결과도 그대로 나온다.
     * 매치된 카테고리의 하위 카테고리 id까지 전부 포함한다 — "반려동물"처럼 최상위 카테고리는
     * 그 자신에 상품이 직접 붙지 않고 하위 카테고리("반려용품")에 상품이 있으므로, 자기 id만
     * 필터로 걸면 결과가 0건이 되어버린다.
     */
    private List<Long> resolveCategoryIds(String keyword) {
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

    private List<Long> searchKeywordIds(Query keywordQuery) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(keywordQuery)
                .withMaxResults(MAX_RESULTS)
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream()
                .map(hit -> hit.getContent().projectId())
                .toList();
    }

    /** top-level knn 검색 — retriever 프레임워크가 아닌 8.0부터 GA된 기본 kNN 기능이라 라이선스 제약이 없다. */
    private List<Long> searchKnnIds(float[] queryVector) {
        List<Float> vectorList = new ArrayList<>(queryVector.length);
        for (float f : queryVector) {
            vectorList.add(f);
        }
        SearchResponse<ProjectDocument> response;
        try {
            response = elasticsearchClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(MAX_RESULTS)
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(vectorList)
                                    .k(20)
                                    .numCandidates(100)
                                    .similarity(2 * KNN_SIMILARITY_THRESHOLD - 1)
                            ),
                    ProjectDocument.class
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (response == null || response.hits() == null || response.hits().hits() == null) {
            return List.of();
        }
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(doc -> doc != null && doc.projectId() != null)
                .map(ProjectDocument::projectId)
                .toList();
    }

    /** RRF 공식을 직접 계산해 두 순위 리스트를 합친다 (키워드 매칭 우선 가중치 적용). */
    private List<Long> fuseByRank(List<Long> keywordIds, List<Long> knnIds) {
        Map<Long, Double> scores = new HashMap<>();
        addRankScores(scores, keywordIds, KEYWORD_RRF_WEIGHT);
        addRankScores(scores, knnIds, KNN_RRF_WEIGHT);

        Comparator<Map.Entry<Long, Double>> comparator = Map.Entry.<Long, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry.<Long, Double>comparingByKey().reversed());

        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() >= RRF_MIN_SCORE)
                .sorted(comparator)
                .limit(MAX_RESULTS)
                .map(Map.Entry::getKey)
                .toList();
    }

    private void addRankScores(Map<Long, Double> scores, List<Long> rankedIds, double weight) {
        for (int i = 0; i < rankedIds.size(); i++) {
            double score = weight / (RRF_RANK_CONSTANT + i + 1);
            scores.merge(rankedIds.get(i), score, Double::sum);
        }
    }

    /**
     * ES 장애 시 벡터/nori 매치 없이 DB title LIKE '%keyword%'로 후보를 대신 찾는다(Graceful
     * Degradation) — 예전에 지웠던 LIKE 스텁을 "장애 시에만 켜지는 폴백"으로 되살린 것. categoryId/
     * status/role 필터와 정렬은 호출부(ProjectServiceImpl.findAll)의 buildSpecification이 이
     * candidateProjectIds에 대해 평소와 동일하게 적용하므로 여기선 신경 쓰지 않는다.
     */
    private List<Long> searchFallback(String keyword, Throwable cause) {
        log.warn("프로젝트 검색 호출 실패, DB LIKE 검색으로 대체합니다. 원인: {}", cause.toString());
        return projectRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(escapeLikeWildcards(keyword)).stream()
                .map(Project::getProjectId)
                .toList();
    }

    /**
     * MySQL의 LIKE는 escape 절을 따로 안 줘도 기본적으로 백슬래시를 이스케이프 문자로 쓴다 —
     * 검색어에 든 %, _를 그대로 넘기면 사용자가 의도하지 않은 와일드카드로 해석돼 매칭이 틀어진다.
     */
    private String escapeLikeWildcards(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public void bulkIndex(List<Project> projects) {
        circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_SEARCH_ID).run(
                () -> {
                    doBulkIndex(projects);
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 벌크 색인 실패. 대상 개수={}", projects.size(), cause);
                    return null;
                });
    }

    /**
     * 임베딩이 없는 프로젝트만 새로 생성하고(한 건씩, OpenAI 호출은 구조적으로 배치가 안 됨),
     * 새로 생성된 임베딩은 페이지당 트랜잭션 1번으로 묶어 DB에도 반영한다(ProjectEmbeddingPersister.
     * bulkUpdateEmbeddings) — 건당 트랜잭션을 여는 N+1을 피하는 것이 이 메서드의 목적. 문서 저장은
     * ElasticsearchOperations.save(List)로 페이지당 ES 호출 1번만 나간다.
     */
    private void doBulkIndex(List<Project> projects) {
        Map<Long, float[]> newEmbeddings = new HashMap<>();
        for (Project project : projects) {
            if (project.getEmbedding() == null) {
                float[] embedding = embeddingService.generateEmbeddingForProject(project);
                if (embedding != null) {
                    project.updateEmbedding(embedding);
                    newEmbeddings.put(project.getProjectId(), embedding);
                }
            }
        }
        if (!newEmbeddings.isEmpty()) {
            embeddingPersister.bulkUpdateEmbeddings(newEmbeddings);
        }

        List<Long> projectIds = projects.stream().map(Project::getProjectId).toList();
        Map<Long, List<String>> rewardNamesByProject = rewardRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(Reward::getProjectId, Collectors.mapping(Reward::getName, Collectors.toList())));

        elasticsearchOperations.save(projects.stream()
                .map(project -> toDocument(project, rewardNamesByProject.getOrDefault(project.getProjectId(), List.of())))
                .toList());
    }

    /**
     * 공백으로 나눈 단어마다 title에 대한 prefix 쿼리를 만들고 전부 must(AND)로 묶는다 — 클래스
     * Javadoc 참고. 공백만 있는 검색어(빈 단어 목록)는 ES를 부르지 않고 바로 빈 결과를 반환한다.
     */
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
                .map(hit -> new ProjectSuggestion(hit.getContent().projectId(), hit.getContent().title()))
                .toList();
    }

    private List<ProjectSuggestion> autocompleteFallback(Throwable cause) {
        log.warn("프로젝트 자동완성 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }
}
