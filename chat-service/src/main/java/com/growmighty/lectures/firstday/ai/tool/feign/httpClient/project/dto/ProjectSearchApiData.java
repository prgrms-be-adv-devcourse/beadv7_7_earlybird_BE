package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// project-service의 GET /api/v1/projects 응답(ProjectResponse) 중 검색 tool에 필요한 필드만 뽑은 DTO
public record ProjectSearchApiData(
    Long projectId,
    String title,
    String summary,
    Long categoryId,
    String status,
    BigDecimal goalAmount,
    BigDecimal fundedAmount,
    LocalDate endAt,
    Long thumbnailId
) {
}
