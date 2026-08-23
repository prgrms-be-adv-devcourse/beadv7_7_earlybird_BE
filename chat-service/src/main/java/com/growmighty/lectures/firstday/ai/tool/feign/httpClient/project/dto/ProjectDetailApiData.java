package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectDetailApiData(
    Long projectId,
    Long creatorId,
    Long thumbnailId,
    String title,
    Long categoryId,
    String summary,
    String description,
    BigDecimal goalAmount,
    BigDecimal fundedAmount,
    LocalDateTime startAt,
    LocalDate endAt,
    String status,
    boolean closed,
    String rejectReason,
    LocalDateTime submittedAt,
    LocalDateTime approvedAt,
    LocalDateTime closedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
