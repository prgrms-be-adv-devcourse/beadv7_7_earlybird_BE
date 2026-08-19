package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.RRFRetrieverEntry;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSuggestion;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final int RRF_RANK_CONSTANT = 60;
    /** RRF 스코어 하한: 1/(60+rank) 결합 점수 기준 하위 노이즈 문서 필터링 */
    private static final double RRF_MIN_SCORE = 0.005;
    /** ES 후보 과다조회 한도 — 최종 10개 컷은 ProjectServiceImpl이 MySQL 가시성 필터링 후 수행한다. */
    private static final int AUTOCOMPLETE_CANDIDATE_LIMIT = 50;
    /**
     * 검색어 토큰이 2개 이하면 전부, 3개 이상이면 70%를 일치시켜야 match clause가 통과한다 — nori가
     * 사전에 없는 속어를 음절 단위로 쪼갤 때(예: "냥이"→"냥"+"이") "이"처럼 흔한 조사 음절 하나만
     * 겹쳐도 매치되는 것을 막는다. ES 공식 문서가 typo/부분 매치 튜닝용으로 권장하는 결합 스펙.
     */
    private static final String MATCH_MINIMUM_SHOULD_MATCH = "2<70%";

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;
    private final ProjectRepository projectRepository;
    private final ProjectEmbeddingPersister embeddingPersister;

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
                    elasticsearchOperations.save(toDocument(project));
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), cause);
                    return null;
                });
    }

    private ProjectDocument toDocument(Project project) {
        float[] embedding = project.getEmbedding();
        if (embedding != null && embedding.length == 0) {
            embedding = null;
        }
        return new ProjectDocument(project.getProjectId(), project.getTitle(), project.getSummary(),
                project.getDescription(), embedding);
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
        Query keywordQuery = Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m.field("title").query(keyword).boost(2.0f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                .should(s -> s.match(m -> m.field("summary").query(keyword).boost(1.2f).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))
                .should(s -> s.match(m -> m.field("description").query(keyword).minimumShouldMatch(MATCH_MINIMUM_SHOULD_MATCH)))));

        if (queryVector != null && queryVector.length > 0) {
            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float f : queryVector) {
                vectorList.add(f);
            }
            SearchResponse<ProjectDocument> response;
            try {
                response = elasticsearchClient.search(s -> s
                                .index(INDEX_NAME)
                                .size(MAX_RESULTS)
                                .minScore(RRF_MIN_SCORE)
                                .retriever(r -> r.rrf(rrf -> rrf
                                        .retrievers(
                                                RRFRetrieverEntry.of(e -> e.retriever(r1 -> r1.standard(st -> st.query(keywordQuery)))),
                                                RRFRetrieverEntry.of(e -> e.retriever(r2 -> r2.knn(knn -> knn
                                                        .field("embedding")
                                                        .queryVector(vectorList)
                                                        .k(20)
                                                        .numCandidates(100)
                                                )))
                                        )
                                        .rankConstant(RRF_RANK_CONSTANT)
                                        .rankWindowSize(MAX_RESULTS)
                                )),
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
        } else {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(keywordQuery)
                    .withMaxResults(MAX_RESULTS)
                    .build();
            SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
            return hits.stream().map(hit -> hit.getContent().projectId()).toList();
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
        return projectRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(Project::getProjectId)
                .toList();
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
        elasticsearchOperations.save(projects.stream().map(this::toDocument).toList());
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
