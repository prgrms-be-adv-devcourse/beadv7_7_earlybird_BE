package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                            .similarity(0.78f)
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
}
