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
        reranker = new CohereReranker(client, cbFactory, PROPS);
    }

    /** 관련도 컷 임계값은 운영 기본값(절대 0.08 / 1등 대비 35%)을 그대로 쓴다. */
    private static final CohereRerankProperties PROPS =
            new CohereRerankProperties(true, null, null, 0, "key", 0, 0.08, 0.35);

    /** 점수 배열을 후보(id = 인덱스+1)로 바꿔 rerank를 돌린다. */
    private List<Long> rerankScores(double... scores) {
        List<CohereRerankClient.Ranked> ranked = new java.util.ArrayList<>();
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            ranked.add(new CohereRerankClient.Ranked(i, scores[i]));
            ids.add((long) i + 1);
        }
        when(client.rerank(any(), any())).thenReturn(ranked);
        return reranker.rerank("q", ids, docs(ids.toArray(new Long[0])));
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

    /**
     * 예전엔 Cohere가 일부만 반환하면 누락분을 뒤에 붙였다. 관련도 컷(#763)을 넣으면서 제거했다 —
     * 무조건 붙이면 방금 컷한 후보가 그대로 되살아나기 때문이다. top_n=문서 수라 정상 응답엔 누락이 없다.
     */
    @Test
    void keepsOnlyScoredCandidatesWhenCohereReturnsSubset() {
        List<Long> candidates = List.of(10L, 20L, 30L);
        when(client.rerank(any(), any())).thenReturn(List.of(new CohereRerankClient.Ranked(1, 0.9)));

        assertThat(reranker.rerank("q", candidates, docs(10L, 20L, 30L)))
                .containsExactly(20L);
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

    // --- 관련도 컷 (#763). 점수는 2026-08-30 로컬 실측값(시드 198건) 그대로다. ---

    @Test
    void cutsCandidatesFailingBothAbsoluteAndRelativeFloors() {
        // '반팔 티셔츠': 티셔츠 11건(0.4863~0.6359) + 노이즈 4건(롱코트/데님x2/목걸이)
        assertThat(rerankScores(0.6359, 0.6047, 0.5954, 0.5727, 0.5701, 0.5698,
                0.5390, 0.5333, 0.5210, 0.4911, 0.4863,
                0.0707, 0.0602, 0.0502, 0.0316))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
    }

    @Test
    void keepsLowAbsoluteScoresWhenRatioIsHigh() {
        // '패션' 같은 상위어 쿼리는 후보 40건이 통째로 0.0589~0.1025다. 절대값만 보면 정상 결과가 다 날아간다.
        assertThat(rerankScores(0.1025, 0.0900, 0.0653, 0.0641, 0.0615, 0.0599, 0.0589))
                .hasSize(7);
    }

    @Test
    void keepsLowRatioScoresWhenAbsoluteIsEnough() {
        // '겨울 코트'의 워치 스트랩: 1등 대비 20.2%지만 절대 점수(0.1001)가 하한을 넘어 남는다.
        assertThat(rerankScores(0.4966, 0.4451, 0.1001)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void cutCandidatesAreNotReappended() {
        // '닭 요리 책': 요리 에세이집(0.1468) 외 무관 에세이집 2건은 제외돼야 하고 되살아나면 안 된다.
        assertThat(rerankScores(0.1468, 0.0495, 0.0423)).containsExactly(1L);
    }

    @Test
    void alwaysKeepsTopCandidateEvenIfEverythingIsBelowFloors() {
        assertThat(rerankScores(0.02, 0.001, 0.0005)).containsExactly(1L);
    }
}
