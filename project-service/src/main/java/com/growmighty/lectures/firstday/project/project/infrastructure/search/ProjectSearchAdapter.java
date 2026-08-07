package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.ProjectSearchPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
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
    // NativeQuery 기본 페이지 크기(10)에 걸리면 매치가 10개보다 많을 때 조용히 잘린다 —
    // MySQL 쪽 최종 필터링/정렬을 신뢰하는 후보 집합이라 넉넉하게 잡는다.
    private static final int MAX_RESULTS = 200;

    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingModel embeddingModel;
    private final CircuitBreakerFactory circuitBreakerFactory;

    /**
     * bool "should" 안의 knn 절은 코사인 스코어와 무관하게 "색인 전체에서 가장 가까운 k개"를
     * 무조건 반환한다(문서 수가 k보다 적으면 사실상 전부 매치) — 그래서 별도 유사도 하한이 없으면
     * 키워드와 전혀 무관한 문서까지 "OR" 조건으로 새어 들어온다. embedding 필드가 코사인
     * 유사도(project-index-mapping.json)라 이 값도 코사인 값(-1~1) 기준이다.
     *
     * <p><b>이 기본값(0.5)은 검증되지 않았다.</b> 통합 테스트에서만 확인됐는데, 그 테스트는 1536차원
     * 전체를 균등난수로 채운 합성 벡터를 쓴다 — 서로 다른 텍스트의 코사인 유사도가 거의 항상 0
     * 근방에 몰리는, 실제 임베딩과는 전혀 다른 분포다. 실제 OpenAI 임베딩(예: text-embedding-3-small)은
     * 훨씬 좁은 유사도 분포를 가져서, 의미상 전혀 무관한 텍스트끼리도 코사인 0.5~0.6대를 흔히
     * 찍는다고 알려져 있다 — 그렇다면 이 기본값은 실전에서 사실상 아무것도 걸러내지 못하는
     * 무의미한 필터가 되거나(임계값이 너무 낮음), 반대로 진짜 관련 있는 결과까지 잘라낼 수 있다
     * (임계값이 너무 높음). Task 3/4가 실제 OpenAI 키로 운영 데이터에 붙여보기 전까지는 둘 중
     * 어느 쪽인지 알 수 없다 — 그래서 코드를 고치지 않고 설정으로 튜닝할 수 있게 뺐다. 오늘은
     * 동작이 안 바뀌도록 기본값은 0.5f 그대로 둔다.
     */
    @Value("${project.search.knn-similarity-threshold:0.5}")
    private float knnSimilarityThreshold = 0.5f;

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
                        .similarity(knnSimilarityThreshold)))));
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
