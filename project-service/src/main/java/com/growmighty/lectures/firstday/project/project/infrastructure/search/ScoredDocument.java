package com.growmighty.lectures.firstday.project.project.infrastructure.search;

import java.util.List;

/**
 * kNN/BM25 검색 결과에서 projectId와 원본 점수(rawScore), 정규화 점수(normalizedScore)를 유지하는 내부 DTO.
 */
public record ScoredDocument(Long projectId, double rawScore, double normalizedScore) {

    public ScoredDocument(Long projectId, double rawScore) {
        this(projectId, rawScore, rawScore);
    }

    /**
     * BM25 점수를 포화 곡선(Saturation) 방식으로 [0.0, 1.0] 범위로 정규화한다.
     * Min-Max 방식과 달리 사소한 점수 차이를 인위적으로 0과 1로 극단화하지 않고,
     * 절대적 어휘 일치 강도를 부드럽게 반영한다.
     * 공식: score / (score + saturationK)
     */
    public static List<ScoredDocument> normalizeBm25(List<ScoredDocument> docs, double saturationK) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        return docs.stream()
                .map(d -> {
                    double raw = Math.max(0.0, d.rawScore());
                    double norm = raw / (raw + saturationK);
                    return new ScoredDocument(d.projectId(), raw, norm);
                })
                .toList();
    }

    /**
     * 이미 [0.0, 1.0] 범위인 Vector Raw Cosine 점수는 점수 왜곡을 방지하기 위해
     * Min-Max를 거치지 않고 그대로 normalizedScore로 유지한다.
     */
    public static List<ScoredDocument> asDirectVectorScores(List<ScoredDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        return docs.stream()
                .map(d -> {
                    double clamped = Math.max(0.0, Math.min(1.0, d.rawScore()));
                    return new ScoredDocument(d.projectId(), d.rawScore(), clamped);
                })
                .toList();
    }
}
