package com.growmighty.lectures.firstday.settlement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationPolicy.PgSettlement;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationPolicy.RecoveredPayment;
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

    private static OrderPaymentFact payment(long orderId, String pgOrderId, long amount) {
        return OrderPaymentFact.completed(orderId, pgOrderId, 10L, Money.wons(amount), Instant.parse("2026-07-15T00:00:00Z"));
    }
}
