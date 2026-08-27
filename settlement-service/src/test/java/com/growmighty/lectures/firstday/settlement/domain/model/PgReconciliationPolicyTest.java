package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationPolicy.PgSettlement;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationPolicy.RecoveredPayment;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationPolicy.SettlementComparison;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PgReconciliationPolicyTest {

    @Test
    void matchesOnlyTheSamePgOrderIdsAndAmounts() {
        List<OrderPaymentFact> payments = List.of(payment(1, "pg-1", 100), payment(2, "pg-2", 200));

        assertThat(PgReconciliationPolicy.matchesSettlements(
                payments,
                List.of(new PgSettlement("pg-2", Money.wons(200)), new PgSettlement("pg-1", Money.wons(100)))
        )).isTrue();
        assertThat(PgReconciliationPolicy.matchesSettlements(
                payments, List.of(new PgSettlement("pg-1", Money.wons(100)))
        )).isFalse();
        assertThat(PgReconciliationPolicy.matchesSettlements(
                payments,
                List.of(new PgSettlement("pg-1", Money.wons(100)), new PgSettlement("pg-2", Money.wons(200)),
                        new PgSettlement("pg-3", Money.wons(300)))
        )).isFalse();
        assertThat(PgReconciliationPolicy.matchesSettlements(
                payments, List.of(new PgSettlement("pg-1", Money.wons(100)), new PgSettlement("pg-2", Money.wons(201)))
        )).isFalse();
        assertThat(PgReconciliationPolicy.matchesSettlements(
                payments, List.of(new PgSettlement("pg-1", Money.wons(100)), new PgSettlement("pg-1", Money.wons(100)))
        )).isFalse();
    }

    @Test
    void matchesOnlyACompletePaidRecovery() {
        List<OrderPaymentFact> payments = List.of(payment(1, "pg-1", 100));

        assertThat(PgReconciliationPolicy.matchesRecoveredPayments(
                payments, List.of(new RecoveredPayment(1L, 10L, "pg-1", Money.wons(100), true))
        )).isTrue();
        assertThat(PgReconciliationPolicy.matchesRecoveredPayments(
                payments, List.of(new RecoveredPayment(1L, 10L, "pg-1", Money.wons(100), false))
        )).isFalse();
    }

    @Test
    void classifiesOnlyMismatchedRecoveredPaymentAsReviewRequired() {
        List<OrderPaymentFact> payments = List.of(payment(1, "pg-1", 100), payment(2, "pg-2", 200));

        SettlementComparison comparison = PgReconciliationPolicy.classifyRecoveredPayments(
                payments,
                List.of(
                        new RecoveredPayment(1L, 10L, "pg-1", Money.wons(100), true),
                        new RecoveredPayment(2L, 10L, "pg-2", Money.wons(201), true)
                )
        );

        assertThat(comparison.confirmedOrderIds()).containsExactly(1L);
        assertThat(comparison.reviewRequiredOrderIds()).containsExactly(2L);
        assertThat(comparison.requiresReview()).isTrue();
    }

    @Test
    void classifiesOnlyMismatchedPaymentAsReviewRequired() {
        List<OrderPaymentFact> payments = List.of(payment(1, "pg-1", 100), payment(2, "pg-2", 200));

        SettlementComparison comparison = PgReconciliationPolicy.classifySettlements(
                payments,
                List.of(
                        new PgSettlement("pg-1", Money.wons(100)),
                        new PgSettlement("pg-2", Money.wons(201)),
                        new PgSettlement("pg-only", Money.wons(300))
                )
        );

        assertThat(comparison.confirmedOrderIds()).containsExactly(1L);
        assertThat(comparison.reviewRequiredOrderIds()).containsExactly(2L);
        assertThat(comparison.requiresReview()).isTrue();
    }

    @Test
    void classifiesPaymentWithDuplicatePgSettlementAsReviewRequired() {
        SettlementComparison comparison = PgReconciliationPolicy.classifySettlements(
                List.of(payment(1, "pg-1", 100)),
                List.of(new PgSettlement("pg-1", Money.wons(100)), new PgSettlement("pg-1", Money.wons(100)))
        );

        assertThat(comparison.confirmedOrderIds()).isEmpty();
        assertThat(comparison.reviewRequiredOrderIds()).containsExactly(1L);
        assertThat(comparison.requiresReview()).isTrue();
    }

    private static OrderPaymentFact payment(long orderId, String pgOrderId, long amount) {
        return OrderPaymentFact.completed(orderId, pgOrderId, 10L, Money.wons(amount), Instant.parse("2026-07-15T00:00:00Z"));
    }
}
