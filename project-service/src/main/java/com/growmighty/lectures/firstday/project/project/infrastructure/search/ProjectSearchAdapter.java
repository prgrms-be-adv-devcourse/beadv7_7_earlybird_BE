package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private static final int KNN_K = 20;
    private static final int KNN_NUM_CANDIDATES = 100;
    // bool "should" 안의 knn 절은 코사인 스코어와 무관하게 "색인 전체에서 가장 가까운 k개"를
    // 무조건 반환한다(문서 수가 k보다 적으면 사실상 전부 매치) — 그래서 별도 유사도 하한이 없으면
    // 키워드와 전혀 무관한 문서까지 "OR" 조건으로 새어 들어온다. embedding 필드가 코사인
    // 유사도(project-index-mapping.json)라 이 값도 코사인 값(-1~1) 기준이다. 의미상 무관한 텍스트의
    // 코사인 유사도는 보통 낮게 깔리므로, 0.5는 "제목이 진짜 비슷한 주제일 때만" 통과시키는 보수적인 하한이다.
    private static final float KNN_SIMILARITY_THRESHOLD = 0.5f;
    // NativeQuery 기본 페이지 크기(10)에 걸리면 매치가 10개보다 많을 때 조용히 잘린다 —
    // MySQL 쪽 최종 필터링/정렬을 신뢰하는 후보 집합이라 넉넉하게 잡는다.
    private static final int MAX_RESULTS = 200;

    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingModel embeddingModel;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public void index(Project project) {
        try {
            String text = String.join(" ",
                    project.getTitle(),
                    project.getSummary() != null ? project.getSummary() : "",
                    project.getDescription() != null ? project.getDescription() : "");
            float[] embedding = embeddingModel.embed(text);
            elasticsearchOperations.save(new ProjectDocument(
                    project.getProjectId(), project.getTitle(), project.getSummary(),
                    project.getDescription(), embedding));
        } catch (RuntimeException e) {
            log.warn("프로젝트 검색 색인 실패. projectId={}", project.getProjectId(), e);
        }
    }

    @Override
    public void remove(Long projectId) {
        try {
            elasticsearchOperations.delete(String.valueOf(projectId), ProjectDocument.class);
        } catch (RuntimeException e) {
            log.warn("프로젝트 검색 색인 삭제 실패. projectId={}", projectId, e);
        }
    }

    @Override
    public List<Long> search(String keyword) {
        return circuitBreakerFactory.create("projectSearch").run(
                () -> doSearch(keyword),
                this::searchFallback);
    }

    private List<Long> doSearch(String keyword) {
        List<Float> queryVector = toFloatList(embeddingModel.embed(keyword));
        Query query = Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m.field("title").query(keyword)))
                .should(s -> s.match(m -> m.field("summary").query(keyword)))
                .should(s -> s.match(m -> m.field("description").query(keyword)))
                .should(s -> s.knn(k -> k
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(KNN_K)
                        .numCandidates(KNN_NUM_CANDIDATES)
                        .similarity(KNN_SIMILARITY_THRESHOLD)))));
        NativeQuery nativeQuery = NativeQuery.builder().withQuery(query).withMaxResults(MAX_RESULTS).build();
        SearchHits<ProjectDocument> hits = elasticsearchOperations.search(nativeQuery, ProjectDocument.class);
        return hits.stream().map(hit -> hit.getContent().projectId()).toList();
    }

    private List<Long> searchFallback(Throwable cause) {
        log.warn("프로젝트 검색 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
    }

    private List<Float> toFloatList(float[] values) {
        List<Float> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(value);
        }
        return list;
    }
}
