package com.growmighty.lectures.firstday.ai.tool.feign.port.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReviewSearchResult(
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
