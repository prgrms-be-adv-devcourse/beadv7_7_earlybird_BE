package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final CohereRerankProperties props;

    public CohereReranker(CohereRerankClient client, CircuitBreakerFactory circuitBreakerFactory,
                          CohereRerankProperties props) {
        this.client = client;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.props = props;
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

        List<CohereRerankClient.Ranked> byScoreDesc = ranked.stream()
                .sorted(Comparator.comparingDouble(CohereRerankClient.Ranked::relevanceScore).reversed())
                .toList();
        double topScore = byScoreDesc.get(0).relevanceScore();

        Set<Long> kept = new LinkedHashSet<>();
        int cut = 0;
        for (CohereRerankClient.Ranked r : byScoreDesc) {
            if (r.index() < 0 || r.index() >= candidateIds.size()) {
                continue;
            }
            // 1등은 무조건 남긴다 — 전부 점수가 낮은 쿼리에서 결과가 통째로 비는 걸 막는다.
            if (kept.isEmpty() || !isIrrelevant(r.relevanceScore(), topScore)) {
                kept.add(candidateIds.get(r.index()));
            } else {
                cut++;
            }
        }
        if (cut > 0) {
            log.debug("[Rerank] 관련도 컷: 후보 {}개 중 {}개 제외 (1등 점수 {})", candidateIds.size(), cut, topScore);
        }
        // 누락분(Cohere가 점수를 안 준 후보)은 붙이지 않는다 — top_n=문서 수라 정상 응답이면 누락이 없고,
        // 무조건 붙이면 방금 컷한 후보가 그대로 되살아난다.
        return new ArrayList<>(kept);
    }

    /**
     * 절대 점수와 1등 대비 비율이 <b>둘 다</b> 미달일 때만 무관으로 본다.
     *
     * <p>한쪽만으로 자르면 안 되는 이유(2026-08-30 실측, 시드 198건):
     * 상위어 쿼리는 후보 전체의 절대 점수가 낮게 깔린다 — {@code "패션"}은 40건이 0.0589~0.1025,
     * {@code "책"}은 33건이 0.0986~0.1282다. 절대값만 보면 정상 결과가 통째로 날아간다.
     * 반대로 비율만 보면 {@code "강아지 간식"}의 하위권(1등 대비 6~8%인 산책줄)처럼
     * 명백한 노이즈를 못 거른다. 둘 다 미달인 것만 잘라야 양쪽이 산다.
     */
    private boolean isIrrelevant(double score, double topScore) {
        boolean belowAbsolute = score < props.minRelevanceScore();
        boolean belowRelative = topScore <= 0 || score < topScore * props.minRelativeRatio();
        return belowAbsolute && belowRelative;
    }
}
