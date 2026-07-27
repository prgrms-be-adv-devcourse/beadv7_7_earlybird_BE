package com.growmighty.lectures.firstday.project.project.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectTest {

    private Project project(long goalAmount, LocalDate endAt) {
        return Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(goalAmount), LocalDateTime.now(), endAt);
    }

    private Project project() {
        return project(1_000_000, LocalDate.now().plusDays(30));
    }

    private Project publishedProject() {
        Project project = project();
        project.approve();
        return project;
    }

    @Test
    @DisplayName("등록하면 심사 대기 상태이고 모금액은 0으로 시작한다")
    void register_startsAsPendingReviewWithZeroFunded() {
        Project project = project();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.PENDING_REVIEW);
        assertThat(project.getFundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(project.isPublished()).isFalse();
    }

    @Test
    @DisplayName("목표 금액이 0 이하면 등록할 수 없다")
    void register_invalidGoalAmount_throws() {
        assertThatThrownBy(() -> project(0, LocalDate.now().plusDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("마감일이 시작일 이후가 아니면 등록할 수 없다")
    void register_endAtNotAfterStartAt_throws() {
        LocalDateTime startAt = LocalDateTime.now();
        assertThatThrownBy(() -> Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, startAt.toLocalDate()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("마감일이 시작일로부터 정확히 3개월 이내면 등록할 수 있다")
    void register_endAtExactlyThreeMonthsAfterStartAt_succeeds() {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 27, 0, 0);
        LocalDate endAt = LocalDate.of(2026, 4, 27);

        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, endAt);

        assertThat(project.getEndAt()).isEqualTo(endAt);
    }

    @Test
    @DisplayName("마감일이 시작일로부터 3개월을 초과하면 등록할 수 없다")
    void register_endAtExceedsThreeMonths_throws() {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 27, 0, 0);
        LocalDate endAt = LocalDate.of(2026, 4, 28);

        assertThatThrownBy(() -> Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, endAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인하면 심사 대기에서 진행중으로 바뀌고, 대기 상태가 아니면 승인할 수 없다")
    void approve_pendingReviewToInProgress() {
        Project project = project();
        project.approve();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(project.isPublished()).isTrue();

        assertThatThrownBy(project::approve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("반려하면 심사 대기에서 반려 상태로 바뀌고 사유가 저장된다")
    void reject_pendingReviewToRejected() {
        Project project = project();
        project.reject("부적절한 내용");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.REJECTED);
        assertThat(project.getRejectReason()).isEqualTo("부적절한 내용");
        assertThat(project.isPublished()).isFalse();
    }

    @Test
    @DisplayName("진행중 상태에서 마감일이 남아있으면 주문을 받을 수 있다")
    void isOpen_inProgressAndBeforeDeadline_true() {
        Project project = publishedProject();
        assertThat(project.isOpen()).isTrue();
    }

    @Test
    @DisplayName("심사 대기 상태면 마감일이 남아있어도 주문을 받을 수 없다")
    void isOpen_notPublished_false() {
        Project project = project();
        assertThat(project.isOpen()).isFalse();
    }

    @Test
    @DisplayName("마감일 당일은 하루 종일 포함되어 진행중이면 주문을 받을 수 있다 (2026-07-22 결정)")
    void isOpen_deadlineIsToday_true() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now().minusDays(1), LocalDate.now());
        project.approve();
        assertThat(project.isOpen()).isTrue();
    }

    @Test
    @DisplayName("마감일이 어제 이전으로 지났으면 진행중이어도 주문을 받을 수 없다")
    void isOpen_deadlinePassed_false() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now().minusDays(2), LocalDate.now().minusDays(1));
        project.approve();
        assertThat(project.isOpen()).isFalse();
    }

    @Test
    @DisplayName("공개 전에는 필드를 자유롭게 수정할 수 있고 null 필드는 바뀌지 않는다")
    void updateBeforePublish_changesOnlyNonNullFields() {
        Project project = project();
        LocalDate newEndAt = LocalDate.now().plusDays(60);

        project.updateBeforePublish("새 제목", null, null, null, null, BigDecimal.valueOf(2_000_000), null, newEndAt);

        assertThat(project.getTitle()).isEqualTo("새 제목");
        assertThat(project.getSummary()).isEqualTo("summary");
        assertThat(project.getGoalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
        assertThat(project.getEndAt()).isEqualTo(newEndAt);
    }

    @Test
    @DisplayName("공개 후에는 updateBeforePublish로 수정할 수 없다")
    void updateBeforePublish_afterPublish_throws() {
        Project project = publishedProject();
        assertThatThrownBy(() -> project.updateBeforePublish("새 제목", null, null, null, null, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("공개 후에는 summary/description/thumbnailId만 수정된다")
    void updateAfterPublish_onlyAllowedFields() {
        Project project = publishedProject();
        String originalTitle = project.getTitle();

        project.updateAfterPublish("새 요약", null, 99L);

        assertThat(project.getSummary()).isEqualTo("새 요약");
        assertThat(project.getDescription()).isEqualTo("desc");
        assertThat(project.getThumbnailId()).isEqualTo(99L);
        assertThat(project.getTitle()).isEqualTo(originalTitle);
    }

    @Test
    @DisplayName("마감일은 현재 마감일 이후로만 연장할 수 있다")
    void extendDeadline_onlyForward() {
        Project project = publishedProject();
        LocalDate currentEndAt = project.getEndAt();

        project.extendDeadline(currentEndAt.plusDays(10));
        assertThat(project.getEndAt()).isEqualTo(currentEndAt.plusDays(10));

        assertThatThrownBy(() -> project.extendDeadline(currentEndAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("마감일 연장도 시작일로부터 3개월을 초과할 수 없다")
    void extendDeadline_beyondThreeMonthsFromStartAt_throws() {
        LocalDateTime startAt = LocalDateTime.of(2026, 1, 27, 0, 0);
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), startAt, LocalDate.of(2026, 2, 1));
        project.approve();

        project.extendDeadline(LocalDate.of(2026, 4, 27));
        assertThat(project.getEndAt()).isEqualTo(LocalDate.of(2026, 4, 27));

        assertThatThrownBy(() -> project.extendDeadline(LocalDate.of(2026, 4, 28)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("배치 마감 처리는 진행중 상태에서만 가능하다")
    void closeByDeadline_notInProgress_throws() {
        Project project = project();
        assertThatThrownBy(project::closeByDeadline).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("모금액이 목표 금액에 못 미치면 마감 시 실패로 확정된다")
    void closeByDeadline_belowGoal_fails() {
        Project project = publishedProject();
        project.closeByDeadline();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);
        assertThat(project.isClosed()).isTrue();
        assertThat(project.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("모금액이 목표 금액 이상이면 마감 시 성공으로 확정된다")
    void closeByDeadline_reachesGoal_succeeds() {
        Project project = publishedProject();
        // fundedAmount는 아직 결제 이벤트로 채워주는 트리거가 없어(Project.java 필드 TODO 참고)
        // 판정 로직만 독립적으로 검증하기 위해 리플렉션으로 직접 값을 넣는다.
        ReflectionTestUtils.setField(project, "fundedAmount", project.getGoalAmount());

        project.closeByDeadline();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.SUCCEEDED);
        assertThat(project.isClosed()).isTrue();
    }

    @Test
    @DisplayName("목표 금액을 이미 달성했으면 마감일 전에도 조기 종료해 성공으로 확정할 수 있다")
    void closeEarlyAsSucceeded_goalReached_succeeds() {
        Project project = publishedProject();
        ReflectionTestUtils.setField(project, "fundedAmount", project.getGoalAmount());

        project.closeEarlyAsSucceeded();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.SUCCEEDED);
        assertThat(project.isClosed()).isTrue();
        assertThat(project.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("목표 금액 미달이면 조기 종료할 수 없다")
    void closeEarlyAsSucceeded_belowGoal_throws() {
        Project project = publishedProject();
        assertThatThrownBy(project::closeEarlyAsSucceeded)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("목표 금액을 아직 달성하지 못해");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("조기 종료도 진행중 상태에서만 가능하다")
    void closeEarlyAsSucceeded_notInProgress_throws() {
        Project project = project();
        assertThatThrownBy(project::closeEarlyAsSucceeded).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("진행중이면 취소할 수 있다")
    void cancel_inProgress_succeeds() {
        Project project = publishedProject();
        project.cancel();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELLED);
        assertThat(project.isClosed()).isTrue();
        assertThat(project.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 성공(SUCCEEDED)했어도 취소할 수 있다")
    void cancel_succeeded_succeeds() {
        Project project = publishedProject();
        ReflectionTestUtils.setField(project, "fundedAmount", project.getGoalAmount());
        project.closeEarlyAsSucceeded();

        project.cancel();

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CANCELLED);
    }

    @Test
    @DisplayName("심사 대기/반려 상태는 취소할 수 없다")
    void cancel_notPublished_throws() {
        Project project = project();
        assertThatThrownBy(project::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 실패(FAILED)한 프로젝트는 취소할 수 없다 — 이미 환불 파이프라인을 타는 상태라 취소 대상이 아니다")
    void cancel_failed_throws() {
        Project project = publishedProject();
        project.closeByDeadline();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);

        assertThatThrownBy(project::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 취소된 프로젝트는 다시 취소할 수 없다")
    void cancel_alreadyCancelled_throws() {
        Project project = publishedProject();
        project.cancel();

        assertThatThrownBy(project::cancel).isInstanceOf(IllegalStateException.class);
    }
}
