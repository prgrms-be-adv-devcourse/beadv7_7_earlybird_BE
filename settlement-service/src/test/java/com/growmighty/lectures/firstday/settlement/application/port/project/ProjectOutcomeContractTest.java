package com.growmighty.lectures.firstday.settlement.application.port.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectOutcomeContractTest {

    @Test
    @DisplayName("Project가 판정한 성공·실패·취소 결과를 보존한다")
    void preservesProjectOutcomes() {
        ProjectOutcomeReader reader = () -> List.of(
                new ProjectOutcome(101L, 201L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(102L, 202L, ProjectOutcomeStatus.FAILED),
                new ProjectOutcome(103L, 203L, ProjectOutcomeStatus.CANCELLED)
        );

        List<ProjectOutcome> outcomes = reader.findProjectOutcomes();

        assertThat(outcomes).containsExactly(
                new ProjectOutcome(101L, 201L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(102L, 202L, ProjectOutcomeStatus.FAILED),
                new ProjectOutcome(103L, 203L, ProjectOutcomeStatus.CANCELLED)
        );
    }

    @Test
    @DisplayName("유효하지 않은 프로젝트 식별자를 거부한다")
    void rejectsInvalidProjectId() {
        assertThatThrownBy(() -> new ProjectOutcome(null, 201L, ProjectOutcomeStatus.SUCCEEDED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectOutcome(0L, 201L, ProjectOutcomeStatus.SUCCEEDED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효하지 않은 창작자 식별자를 거부한다")
    void rejectsInvalidCreatorId() {
        assertThatThrownBy(() -> new ProjectOutcome(101L, null, ProjectOutcomeStatus.SUCCEEDED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectOutcome(101L, 0L, ProjectOutcomeStatus.SUCCEEDED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Project 결과 상태가 누락되면 거부한다")
    void rejectsMissingStatus() {
        assertThatThrownBy(() -> new ProjectOutcome(101L, 201L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
