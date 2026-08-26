package com.growmighty.lectures.firstday.project.project.infrastructure.search;

/**
 * 프로젝트 1건에 대한 5개 필드별 독립 임베딩 벡터 DTO.
 */
public record ProjectFieldVectors(
        float[] titleVector,
        float[] summaryVector,
        float[] descriptionVector,
        float[] categoryVector,
        float[] rewardVector
) {
    public static ProjectFieldVectors empty() {
        return new ProjectFieldVectors(null, null, null, null, null);
    }

    public boolean hasAnyVector() {
        return titleVector != null || summaryVector != null || descriptionVector != null
                || categoryVector != null || rewardVector != null;
    }
}
