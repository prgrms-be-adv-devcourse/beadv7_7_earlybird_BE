package com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectSearchResult(
    Long projectId,
    String title,
    String summary,
    Long categoryId,
    String status,
    BigDecimal goalAmount,
    BigDecimal fundedAmount,
    LocalDate endAt,
    String thumbnailUrl
) {
}
