package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cohere.rerank.*} — {@code enabled=true}일 때만 {@link CohereReranker}/{@link CohereRerankClient}
 * Bean이 생성되고 {@code apiKey}가 참조된다. {@code enabled=false}면 이 값들은 무시되고 {@link NoOpReranker}가 활성.
 */
@ConfigurationProperties(prefix = "cohere.rerank")
public record CohereRerankProperties(
        boolean enabled,
        String baseUrl,
        String model,
        int topN,
        String apiKey,
        long timeoutMs,
        double minRelevanceScore,
        double minRelativeRatio
) {

    public CohereRerankProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.cohere.com";
        }
        if (model == null || model.isBlank()) {
            model = "rerank-v3.5";
        }
        if (topN <= 0) {
            topN = 40;
        }
        if (timeoutMs <= 0) {
            timeoutMs = 3000;
        }
        // 관련도 컷 기본값은 실측(2026-08-30, 시드 198건)에서 뽑았다 — 근거는 CohereReranker 주석 참고.
        if (minRelevanceScore <= 0) {
            minRelevanceScore = 0.08;
        }
        if (minRelativeRatio <= 0) {
            minRelativeRatio = 0.35;
        }
    }
}
