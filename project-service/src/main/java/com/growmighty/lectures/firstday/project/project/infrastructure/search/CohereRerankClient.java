package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cohere Rerank v2 REST 호출. 요청 조립과 응답 파싱만 담당하고 예외는 그대로 전파한다
 * (재시도/폴백은 {@link CohereReranker}가 CircuitBreaker로 처리).
 */
@RequiredArgsConstructor
public class CohereRerankClient {

    private final RestClient cohereRestClient;
    private final CohereRerankProperties props;

    /** 관련도 내림차순으로 정렬된 (원본 인덱스, 점수). */
    public record Ranked(int index, double relevanceScore) {}

    private record Request(String model, String query, List<String> documents,
                           @JsonProperty("top_n") int topN) {}

    private record Response(List<Result> results) {}

    private record Result(int index, @JsonProperty("relevance_score") double relevanceScore) {}

    public List<Ranked> rerank(String query, List<String> documents) {
        Request body = new Request(props.model(), query, documents, documents.size());
        Response resp = cohereRestClient.post()
                .uri("/v2/rerank")
                .body(body)
                .retrieve()
                .body(Response.class);
        if (resp == null || resp.results() == null) {
            return List.of();
        }
        return resp.results().stream()
                .map(r -> new Ranked(r.index(), r.relevanceScore()))
                .toList();
    }
}
