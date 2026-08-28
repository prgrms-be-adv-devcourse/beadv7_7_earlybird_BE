package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cohere Rerank로 후보를 재정렬한다. 원본 쿼리(확장 아님)와 {@code title + " " + summary}를 넘긴다.
 * 실패/타임아웃/CircuitBreaker Open 시 candidateIds를 그대로 반환 — 검색은 fusion 순서로 graceful degrade.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cohere.rerank", name = "enabled", havingValue = "true")
public class CohereReranker implements Reranker {

    private final CohereRerankClient client;
    private final CircuitBreakerFactory circuitBreakerFactory;

    public CohereReranker(CohereRerankClient client, CircuitBreakerFactory circuitBreakerFactory) {
        this.client = client;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public List<Long> rerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        return circuitBreakerFactory.create(ProjectSearchCircuitBreakerConfig.PROJECT_RERANK_ID).run(
                () -> doRerank(query, candidateIds, docs),
                cause -> {
                    log.warn("[Rerank] Cohere 호출 실패 → fusion 순서 유지. 원인: {}", cause.toString());
                    return candidateIds;
                });
    }

    private List<Long> doRerank(String query, List<Long> candidateIds, Map<Long, ProjectDocument> docs) {
        List<String> documents = new ArrayList<>(candidateIds.size());
        for (Long id : candidateIds) {
            ProjectDocument d = docs.get(id);
            String title = (d != null && d.title() != null) ? d.title() : "";
            String summary = (d != null && d.summary() != null) ? d.summary() : "";
            documents.add((title + " " + summary).trim());
        }

        List<CohereRerankClient.Ranked> ranked = client.rerank(query, documents);
        if (ranked.isEmpty()) {
            return candidateIds;
        }

        Set<Long> reordered = new LinkedHashSet<>();
        for (CohereRerankClient.Ranked r : ranked) {
            if (r.index() >= 0 && r.index() < candidateIds.size()) {
                reordered.add(candidateIds.get(r.index()));
            }
        }
        // Cohere가 일부만 반환한 경우 누락분을 원래 순서로 뒤에 붙인다.
        reordered.addAll(candidateIds);
        return new ArrayList<>(reordered);
    }
}
