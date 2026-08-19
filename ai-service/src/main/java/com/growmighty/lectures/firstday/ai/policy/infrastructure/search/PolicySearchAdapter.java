package com.growmighty.lectures.firstday.ai.policy.infrastructure.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;
import com.growmighty.lectures.firstday.ai.policy.infrastructure.embedding.PolicyEmbeddingService;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySearchAdapter implements PolicySearchPort {

    private static final int MAX_RESULTS = 10;
    private static final int KNN_K = 5;
    private static final int KNN_NUM_CANDIDATES = 30;

    private final ElasticsearchOperations elasticsearchOperations;
    private final CircuitBreakerFactory circuitBreakerFactory;
    private final PolicyEmbeddingService embeddingService;

    @Override
    public List<PolicyChunkResult> search(String query, PolicyCategory category) {
        return circuitBreakerFactory.create(PolicySearchCircuitBreakerConfig.POLICY_SEARCH_ID).run(
            () -> doSearch(query, category),
            this::searchFallback);
    }

    private List<PolicyChunkResult> doSearch(String query, PolicyCategory category) {
        float[] queryVector = embeddingService.embedQuery(query);
        Query esQuery = buildQuery(query, category, queryVector);

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(esQuery)
            .withMaxResults(MAX_RESULTS)
            .build();
        SearchHits<PolicyDocument> hits = elasticsearchOperations.search(nativeQuery, PolicyDocument.class);
        return hits.stream()
            .map(hit -> toResult(hit.getContent()))
            .toList();
    }

    private Query buildQuery(String query, PolicyCategory category, float[] queryVector) {
        return Query.of(q -> q.bool(b -> {
            b.should(s -> s.match(m -> m.field("topic").query(query).boost(1.5f)))
                .should(s -> s.match(m -> m.field("content").query(query)));
            if (queryVector != null && queryVector.length > 0) {
                b.should(s -> s.knn(k -> k
                    .field("embedding")
                    .queryVector(toFloatList(queryVector))
                    .k(KNN_K)
                    .numCandidates(KNN_NUM_CANDIDATES)
                    .boost(10.0f)));
            }
            if (category != null) {
                b.filter(f -> f.term(t -> t.field("category").value(category.name())));
            }
            return b;
        }));
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float f : vector) {
            list.add(f);
        }
        return list;
    }

    private PolicyChunkResult toResult(PolicyDocument document) {
        return new PolicyChunkResult(document.category(), document.topic(), document.content());
    }

    private List<PolicyChunkResult> searchFallback(Throwable cause) {
        log.warn("정책 검색 호출 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("정책 검색 서비스가 일시적으로 응답하지 않습니다. 잠시 후에 다시 시도해 주세요.");
    }

    @Override
    public void reindexAll(List<PolicyDocument> documents) {
        circuitBreakerFactory.create(PolicySearchCircuitBreakerConfig.POLICY_REINDEX_ID).run(
            () -> {
                doReindexAll(documents);
                return null;
            },
            cause -> reindexFallback(cause));
    }

    private void doReindexAll(List<PolicyDocument> documents) {
        elasticsearchOperations.delete(
            DeleteQuery.builder(org.springframework.data.elasticsearch.core.query.Query.findAll()).build(),
            PolicyDocument.class);
        elasticsearchOperations.save(documents);
    }

    private Void reindexFallback(Throwable cause) {
        log.warn("정책 재색인 실패. 원인: {}", cause.toString());
        throw new ServiceUnavailableException("정책 재색인이 실패했습니다.");
    }
}
