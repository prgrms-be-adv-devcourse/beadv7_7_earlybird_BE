package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CohereRerankerTest {

    private final CohereRerankClient client = mock(CohereRerankClient.class);
    private final CircuitBreakerFactory cbFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker cb = mock(CircuitBreaker.class);
    private CohereReranker reranker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(cbFactory.create("projectRerank")).thenReturn(cb);
        when(cb.run(any(Supplier.class), any(Function.class))).thenAnswer(inv -> {
            Supplier<Object> s = inv.getArgument(0);
            Function<Throwable, Object> fb = inv.getArgument(1);
            try {
                return s.get();
            } catch (Throwable t) {
                return fb.apply(t);
            }
        });
        reranker = new CohereReranker(client, cbFactory);
    }

    private Map<Long, ProjectDocument> docs(Long... ids) {
        return Arrays.stream(ids).collect(Collectors.toMap(
                id -> id,
                id -> new ProjectDocument(id, "제목" + id, "요약" + id, null, 1L, List.of(),
                        null, null, null, null, null)));
    }

    @Test
    void reordersByCohereResult() {
        List<Long> candidates = List.of(10L, 20L, 30L);
        when(client.rerank(eq("강아지 옷"), any())).thenReturn(List.of(
                new CohereRerankClient.Ranked(2, 0.9),
                new CohereRerankClient.Ranked(0, 0.5),
                new CohereRerankClient.Ranked(1, 0.1)));

        assertThat(reranker.rerank("강아지 옷", candidates, docs(10L, 20L, 30L)))
                .containsExactly(30L, 10L, 20L);
    }

    @Test
    void appendsMissingCandidatesInOriginalOrderWhenCohereReturnsSubset() {
        List<Long> candidates = List.of(10L, 20L, 30L);
        when(client.rerank(any(), any())).thenReturn(List.of(new CohereRerankClient.Ranked(1, 0.9)));

        assertThat(reranker.rerank("q", candidates, docs(10L, 20L, 30L)))
                .containsExactly(20L, 10L, 30L);
    }

    @Test
    void fallsBackToCandidateOrderOnClientError() {
        List<Long> candidates = List.of(10L, 20L);
        when(client.rerank(any(), any())).thenThrow(new RuntimeException("cohere down"));

        assertThat(reranker.rerank("q", candidates, docs(10L, 20L))).isEqualTo(candidates);
    }

    @Test
    void emptyCandidatesReturnEmpty() {
        assertThat(reranker.rerank("q", List.of(), Map.of())).isEmpty();
    }
}
