package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// board-service의 GET /api/v1/reviews?projectId= 응답(ReviewResponse)와 동일한 모양
public record ReviewSearchApiData(
    Long id,
    Long projectId,
    Long rewardId,
    String rewardName,
    String authorName,
    BigDecimal rating,
    String content,
    LocalDateTime createdAt
) {
}
