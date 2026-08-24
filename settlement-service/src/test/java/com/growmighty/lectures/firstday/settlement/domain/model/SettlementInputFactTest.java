package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementInputFactTest {

    @Test
    @DisplayName("프로젝트 결과가 지급 또는 환불 대상을 결정한다")
    void determinesTargetFromProjectOutcome() {
        Instant occurredAt = Instant.parse("2026-07-31T09:00:00Z");

        ProjectOutcomeFact succeeded = ProjectOutcomeFact.of(
                101L,
                "프로젝트 101",
                9L,
                ProjectOutcomeFact.Outcome.SUCCEEDED,
                occurredAt
        );
        ProjectOutcomeFact failed = ProjectOutcomeFact.of(
                102L,
                "프로젝트 102",
                10L,
                ProjectOutcomeFact.Outcome.FAILED,
                occurredAt
        );

        assertThat(succeeded.requiresPayout()).isTrue();
        assertThat(succeeded.requiresRefund()).isFalse();
        assertThat(failed.requiresPayout()).isFalse();
        assertThat(failed.requiresRefund()).isTrue();
    }

    @Test
    @DisplayName("주문 결제 취소는 완료 원본과 각 결과 시각을 보존한다")
    void preservesCompletedAndCancelledTimes() {
        Instant completedAt = Instant.parse("2026-07-15T04:20:10Z");
        Instant cancelledAt = Instant.parse("2026-07-18T00:05:00Z");
        OrderPaymentFact fact = OrderPaymentFact.completed(
                1001L,
                "PAY-01J2X8P4QW6YV0M3",
                101L,
                Money.wons(50_000),
                completedAt
        );

        fact.cancel("PAY-01J2X8P4QW6YV0M3", 101L, Money.wons(50_000), cancelledAt);

        assertThat(fact.status()).isEqualTo(OrderPaymentFact.Status.CANCELLED);
        assertThat(fact.completedAt()).isEqualTo(completedAt);
        assertThat(fact.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(fact.occurredAt()).isEqualTo(cancelledAt);
    }

    @Test
    @DisplayName("주문 결제 완료와 다른 원본의 취소를 거부한다")
    void rejectsCancellationWithConflictingSource() {
        OrderPaymentFact fact = OrderPaymentFact.completed(
                1001L,
                "PAY-01J2X8P4QW6YV0M3",
                101L,
                Money.wons(50_000),
                Instant.parse("2026-07-15T04:20:10Z")
        );

        assertThatThrownBy(() -> fact.cancel(
                "DIFFERENT-PG-ORDER",
                101L,
                Money.wons(50_000),
                Instant.parse("2026-07-18T00:05:00Z")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("완료 결제는 대사 대기에서 대사 완료로 전이한다")
    void confirmsCompletedPaymentReconciliation() {
        OrderPaymentFact fact = OrderPaymentFact.completed(
                1001L,
                "PAY-01J2X8P4QW6YV0M3",
                101L,
                Money.wons(50_000),
                Instant.parse("2026-07-15T04:20:10Z")
        );

        fact.confirmReconciliation();

        assertThat(fact.reconciliationStatus())
                .isEqualTo(OrderPaymentFact.ReconciliationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("실패 프로젝트는 다음 날부터 결과 시각 이전의 미취소 결제 전체만 환불 대상으로 만든다")
    void determinesRefundablePaymentsFromCompleteInput() {
        Instant outcomeAt = Instant.parse("2026-08-08T09:00:00Z");
        ProjectOutcomeFact outcome = ProjectOutcomeFact.of(
                101L, "프로젝트 101", 9L, ProjectOutcomeFact.Outcome.FAILED, outcomeAt
        );
        OrderPaymentFact completed = OrderPaymentFact.completed(
                1001L, "PG-1001", 101L, Money.wons(50_000), outcomeAt.minusSeconds(60)
        );
        OrderPaymentFact cancelled = OrderPaymentFact.completed(
                1002L, "PG-1002", 101L, Money.wons(30_000), outcomeAt.minusSeconds(60)
        );
        cancelled.cancel("PG-1002", 101L, Money.wons(30_000), outcomeAt.minusSeconds(1));

        assertThat(outcome.refundablePaymentsDueBefore(
                Instant.parse("2026-08-09T00:00:00Z"), List.of(completed, cancelled)
        )).containsExactly(completed);
        assertThat(outcome.refundablePaymentsDueBefore(
                outcomeAt, List.of(completed, cancelled)
        )).isEmpty();
    }
}
