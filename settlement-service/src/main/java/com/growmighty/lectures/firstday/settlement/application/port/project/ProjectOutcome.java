// TODO(settlement-plan): Replace synchronous query DTO usage with the persisted Project event fact shape.
package com.growmighty.lectures.firstday.settlement.application.port.project;

public record ProjectOutcome(
        Long projectId,
        Long creatorId,
        ProjectOutcomeStatus status
) {

    public ProjectOutcome {
        if (projectId == null || projectId <= 0) {
            throw new IllegalArgumentException("프로젝트 식별자는 양수여야 합니다.");
        }
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Project 결과 상태는 필수입니다.");
        }
    }
}
