package com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectDetailResult(
    Long projectId,
    String title,
    Long categoryId,
    String summary,
    String description,
    BigDecimal goalAmount,
    BigDecimal fundedAmount,
    LocalDateTime startAt,
    LocalDate endAt,
    String status,
    boolean closed
) {
}
