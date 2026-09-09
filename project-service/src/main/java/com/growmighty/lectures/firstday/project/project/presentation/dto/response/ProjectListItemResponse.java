package com.growmighty.lectures.firstday.project.project.presentation.dto.response;

import com.growmighty.lectures.firstday.project.project.domain.Project;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목록 조회 전용 응답. {@link ProjectResponse}와 달리 본문(description)을 담지 않는다 —
 * 목록 화면은 카드에 제목/썸네일/모금액만 그리는데, 본문까지 실어 보내면 응답이 프로젝트
 * 수에 비례해 무겁게 커진다(실측: 86건 기준 148KB 중 description이 약 73%).
 *
 * <p>summary는 남긴다 — 프론트가 자동완성 후보를 title/summary로 필터링한다.
 * 본문이 필요한 화면은 단건 조회(GET /api/v1/projects/{projectId})를 쓴다.
 */
public record ProjectListItemResponse(
        Long projectId,
        Long creatorId,
        Long thumbnailId,
        String title,
        Long categoryId,
        String summary,
        BigDecimal goalAmount,
        BigDecimal fundedAmount,
        LocalDateTime startAt,
        LocalDate endAt,
        String status,
        boolean closed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProjectListItemResponse from(Project project) {
        return new ProjectListItemResponse(
                project.getProjectId(),
                project.getCreatorId(),
                project.getThumbnailId(),
                project.getTitle(),
                project.getCategoryId(),
                project.getSummary(),
                project.getGoalAmount(),
                project.getFundedAmount(),
                project.getStartAt(),
                project.getEndAt(),
                project.getStatus().name(),
                project.isClosed(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
