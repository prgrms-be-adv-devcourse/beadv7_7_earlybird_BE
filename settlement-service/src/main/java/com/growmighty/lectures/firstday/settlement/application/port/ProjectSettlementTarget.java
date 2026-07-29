package com.growmighty.lectures.firstday.settlement.application.port;

public record ProjectSettlementTarget(
        Long projectId,
        Long creatorId
) {

    public ProjectSettlementTarget {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
    }
}
