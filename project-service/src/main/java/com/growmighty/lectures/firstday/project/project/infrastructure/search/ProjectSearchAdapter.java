package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * index()/remove()는 ES를 직접 부르지 않고 이벤트만 발행한다.
 * 사전 계산된 임베딩(Project.getEmbedding())을 사용하여 ES에 색인하며,
 * 검색 시 Nori 형태소 분석기 키워드 매칭과 kNN 벡터 하이브리드 검색을 함께 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;
    private static final int MAX_RESULTS = 200;

    private final ElasticsearchOperations elasticsearchOperations;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectEmbeddingService embeddingService;

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
                    float[] embedding = project.getEmbedding();
                    if (embedding != null && embedding.length == 0) {
                        embedding = null;
                    }
                    elasticsearchOperations.save(new ProjectDocument(
                            project.getProjectId(), project.getTitle(), project.getSummary(),
                            project.getDescription(), embedding));
                    return null;
                },
                cause -> {
                    log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), cause);
                    return null;
                });
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
                this::searchFallback);
    }

    private List<Long> doSearch(String keyword) {
        float[] queryVector = embeddingService.generateEmbedding(keyword);
        Query query;
        if (queryVector != null && queryVector.length > 0) {
            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float f : queryVector) {
                vectorList.add(f);
            }
            query = Query.of(q -> q.bool(b -> b
                    .should(s -> s.match(m -> m.field("title").query(keyword).boost(2.0f)))
                    .should(s -> s.match(m -> m.field("summary").query(keyword).boost(1.2f)))
                    .should(s -> s.match(m -> m.field("description").query(keyword)))
                    .should(s -> s.knn(k -> k
                            .field("embedding")
                            .queryVector(vectorList)
                            .k(10)
                            .numCandidates(100)
                            .boost(10.0f)))));
        } else {
            query = Query.of(q -> q.bool(b -> b
                    .should(s -> s.match(m -> m.field("title").query(keyword).boost(2.0f)))
                    .should(s -> s.match(m -> m.field("summary").query(keyword).boost(1.2f)))
                    .should(s -> s.match(m -> m.field("description").query(keyword)))));
        }

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(MAX_RESULTS)
                .build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream().map(hit -> hit.getContent().projectId()).toList();
    }

    private List<Long> searchFallback(Throwable cause) {
        log.warn("프로젝트 검색 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }
}
