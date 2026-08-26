package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SearchScoreVerificationTest {

    @Test
    @DisplayName("ES kNN cosine _score는 (1 + cosine) / 2 공식으로 변환되므로, 2 * _score - 1로 완벽히 raw cosine으로 복원된다")
    void verifyEsCosineScoreDecoding() {
        // cosine = 1.0 (완전 일치) -> ES _score = 1.0 -> raw cosine = 1.0
        double esScore1 = 1.0;
        double rawCosine1 = Math.max(0.0, 2.0 * esScore1 - 1.0);
        assertThat(rawCosine1).isEqualTo(1.0);

        // cosine = 0.0 (직교/무관) -> ES _score = 0.5 -> raw cosine = 0.0
        double esScore0 = 0.5;
        double rawCosine0 = Math.max(0.0, 2.0 * esScore0 - 1.0);
        assertThat(rawCosine0).isEqualTo(0.0);

        // cosine = 0.70 (일반적인 유의미 매칭) -> ES _score = 0.85 -> raw cosine = 0.70
        double esScore70 = 0.85;
        double rawCosine70 = Math.max(0.0, 2.0 * esScore70 - 1.0);
        assertThat(rawCosine70).isCloseTo(0.70, within(1e-6));

        // cosine = -0.5 (반대) -> ES _score = 0.25 -> raw cosine = 0.0 (clamp)
        double esScoreNeg = 0.25;
        double rawCosineNeg = Math.max(0.0, 2.0 * esScoreNeg - 1.0);
        assertThat(rawCosineNeg).isEqualTo(0.0);
    }

    @Test
    @DisplayName("BM25 Saturation 정규화는 점수 차이를 인위적으로 극단화하지 않고 부드러운 [0, 1] 곡선으로 정규화한다")
    void verifyBm25SaturationNormalization() {
        List<ScoredDocument> docs = List.of(
                new ScoredDocument(1L, 15.0), // 15 / (15 + 5) = 0.75
                new ScoredDocument(2L, 5.0),  // 5 / (5 + 5) = 0.50
                new ScoredDocument(3L, 0.0)   // 0 / (0 + 5) = 0.0
        );
        List<ScoredDocument> normalized = ScoredDocument.normalizeBm25(docs, 5.0);

        assertThat(normalized.get(0).normalizedScore()).isCloseTo(0.75, within(1e-6));
        assertThat(normalized.get(1).normalizedScore()).isCloseTo(0.50, within(1e-6));
        assertThat(normalized.get(2).normalizedScore()).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("Vector Raw Cosine 점수는 Min-Max 왜곡 없이 절대 유사도 [0, 1] 범위를 그대로 보존한다")
    void verifyVectorDirectScorePreservation() {
        List<ScoredDocument> docs = List.of(
                new ScoredDocument(1L, 0.95),
                new ScoredDocument(2L, 0.42),
                new ScoredDocument(3L, 0.40)
        );
        List<ScoredDocument> preserved = ScoredDocument.asDirectVectorScores(docs);

        // 0.42와 0.40이 Min-Max로 1.0/0.0으로 왜곡되지 않고 실제 미세한 차이(0.42, 0.40)를 그대로 유지함
        assertThat(preserved.get(0).normalizedScore()).isEqualTo(0.95);
        assertThat(preserved.get(1).normalizedScore()).isEqualTo(0.42);
        assertThat(preserved.get(2).normalizedScore()).isEqualTo(0.40);
    }
}
