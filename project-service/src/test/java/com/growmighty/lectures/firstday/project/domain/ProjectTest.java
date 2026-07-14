package com.growmighty.lectures.firstday.project.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 31, 23, 59);

    private Project project() {
        return Project.register(1L, "수제 가죽 노트커버", "설명", BigDecimal.valueOf(3_000_000), START, END);
    }

    @Test
    @DisplayName("프로젝트는 작성중(DRAFT) 상태로 등록되고, 후원 불가능하다")
    void register_startsAsDraft() {
        Project project = project();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(project.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("목표 금액이 0 이하이거나 마감일이 시작일보다 앞서면 등록할 수 없다")
    void register_invalidValues_throw() {
        assertThatThrownBy(() -> Project.register(1L, "x", "d", BigDecimal.ZERO, START, END))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Project.register(1L, "x", "d", BigDecimal.valueOf(1000), END, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("작성중 → 심사중 → 공개로 전이되면 후원 가능해진다")
    void submitAndApprove_becomesOrderable() {
        Project project = project();

        project.submitForReview();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_REVIEW);
        assertThat(project.isOrderable()).isFalse();

        project.approve();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.OPEN);
        assertThat(project.isOrderable()).isTrue();
    }

    @Test
    @DisplayName("심사중 상태에서 반려되면 후원 불가능하다")
    void reject_notOrderable() {
        Project project = project();
        project.submitForReview();
        project.reject();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.REJECTED);
        assertThat(project.isOrderable()).isFalse();
    }

    @Test
    @DisplayName("작성중 상태에서 바로 승인할 수 없다")
    void approve_fromDraft_throws() {
        assertThatThrownBy(() -> project().approve())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("공개 → 마감 → 달성/실패 판정으로 전이된다")
    void close_thenJudge() {
        Project reached = project();
        reached.submitForReview();
        reached.approve();
        reached.close();
        assertThat(reached.getStatus()).isEqualTo(ProjectStatus.CLOSED);
        reached.markGoalReached();
        assertThat(reached.getStatus()).isEqualTo(ProjectStatus.GOAL_REACHED);

        Project failed = project();
        failed.submitForReview();
        failed.approve();
        failed.close();
        failed.markGoalFailed();
        assertThat(failed.getStatus()).isEqualTo(ProjectStatus.GOAL_FAILED);
    }

    @Test
    @DisplayName("마감 전에는 달성/실패 판정을 할 수 없다")
    void judge_beforeClose_throws() {
        Project project = project();
        project.submitForReview();
        project.approve();

        assertThatThrownBy(project::markGoalReached).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(project::markGoalFailed).isInstanceOf(IllegalStateException.class);
    }
}
