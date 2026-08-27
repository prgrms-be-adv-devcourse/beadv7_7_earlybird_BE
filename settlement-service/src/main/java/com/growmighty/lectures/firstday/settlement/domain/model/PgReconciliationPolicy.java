package com.growmighty.lectures.firstday.settlement.domain.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PgReconciliationPolicy {

    private PgReconciliationPolicy() {
    }

    public static boolean matchesSettlements(
            List<OrderPaymentFact> payments,
            List<PgSettlement> settlements
    ) {
        return !classifySettlements(payments, settlements).requiresReview();
    }

    public static SettlementComparison classifySettlements(
            List<OrderPaymentFact> payments,
            List<PgSettlement> settlements
    ) {
        Map<String, List<OrderPaymentFact>> paymentsByPgOrderId = paymentsByPgOrderId(payments);
        Map<String, List<PgSettlement>> settlementsByPgOrderId = settlementsByPgOrderId(settlements);
        List<Long> confirmedOrderIds = new ArrayList<>();
        List<Long> reviewRequiredOrderIds = new ArrayList<>();

        for (OrderPaymentFact payment : payments) {
            List<OrderPaymentFact> samePayments = paymentsByPgOrderId.get(payment.pgOrderId());
            List<PgSettlement> sameSettlements = settlementsByPgOrderId.get(payment.pgOrderId());
            if (samePayments.size() == 1
                    && sameSettlements != null
                    && sameSettlements.size() == 1
                    && payment.paymentAmount().equals(sameSettlements.getFirst().amount())) {
                confirmedOrderIds.add(payment.orderId());
            } else {
                reviewRequiredOrderIds.add(payment.orderId());
            }
        }

        boolean hasPgOnlySettlement = settlementsByPgOrderId.keySet().stream()
                .anyMatch(pgOrderId -> !paymentsByPgOrderId.containsKey(pgOrderId));
        return new SettlementComparison(
                List.copyOf(confirmedOrderIds),
                List.copyOf(reviewRequiredOrderIds),
                hasPgOnlySettlement || !reviewRequiredOrderIds.isEmpty()
        );
    }

    public static boolean matchesRecoveredPayments(
            List<OrderPaymentFact> payments,
            List<RecoveredPayment> recoveredPayments
    ) {
        return !classifyRecoveredPayments(payments, recoveredPayments).requiresReview();
    }

    public static SettlementComparison classifyRecoveredPayments(
            List<OrderPaymentFact> payments,
            List<RecoveredPayment> recoveredPayments
    ) {
        Map<Long, List<OrderPaymentFact>> paymentsByOrderId = new HashMap<>();
        Map<Long, List<RecoveredPayment>> recoveredPaymentsByOrderId = new HashMap<>();
        List<Long> confirmedOrderIds = new ArrayList<>();
        List<Long> reviewRequiredOrderIds = new ArrayList<>();

        for (OrderPaymentFact payment : payments) {
            paymentsByOrderId.computeIfAbsent(payment.orderId(), ignored -> new ArrayList<>()).add(payment);
        }
        for (RecoveredPayment recovered : recoveredPayments) {
            recoveredPaymentsByOrderId.computeIfAbsent(recovered.orderId(), ignored -> new ArrayList<>()).add(recovered);
        }
        for (OrderPaymentFact payment : payments) {
            List<OrderPaymentFact> samePayments = paymentsByOrderId.get(payment.orderId());
            List<RecoveredPayment> sameRecoveredPayments = recoveredPaymentsByOrderId.get(payment.orderId());
            if (samePayments.size() == 1
                    && sameRecoveredPayments != null
                    && sameRecoveredPayments.size() == 1
                    && matchesRecoveredPayment(payment, sameRecoveredPayments.getFirst())) {
                confirmedOrderIds.add(payment.orderId());
            } else {
                reviewRequiredOrderIds.add(payment.orderId());
            }
        }

        boolean hasRecoveredOnlyPayment = recoveredPaymentsByOrderId.keySet().stream()
                .anyMatch(orderId -> !paymentsByOrderId.containsKey(orderId));
        return new SettlementComparison(
                List.copyOf(confirmedOrderIds),
                List.copyOf(reviewRequiredOrderIds),
                hasRecoveredOnlyPayment || !reviewRequiredOrderIds.isEmpty()
        );
    }

    private static Map<String, List<OrderPaymentFact>> paymentsByPgOrderId(List<OrderPaymentFact> payments) {
        Map<String, List<OrderPaymentFact>> paymentsByPgOrderId = new HashMap<>();
        for (OrderPaymentFact payment : payments) {
            paymentsByPgOrderId.computeIfAbsent(payment.pgOrderId(), ignored -> new ArrayList<>()).add(payment);
        }
        return paymentsByPgOrderId;
    }

    private static Map<String, List<PgSettlement>> settlementsByPgOrderId(List<PgSettlement> settlements) {
        Map<String, List<PgSettlement>> settlementsByPgOrderId = new HashMap<>();
        for (PgSettlement settlement : settlements) {
            settlementsByPgOrderId.computeIfAbsent(settlement.pgOrderId(), ignored -> new ArrayList<>()).add(settlement);
        }
        return settlementsByPgOrderId;
    }

    private static boolean matchesRecoveredPayment(OrderPaymentFact payment, RecoveredPayment recovered) {
        return payment.projectId().equals(recovered.projectId())
                && payment.pgOrderId().equals(recovered.pgOrderId())
                && payment.paymentAmount().equals(recovered.paymentAmount())
                && recovered.paid();
    }

    public record SettlementComparison(
            List<Long> confirmedOrderIds,
            List<Long> reviewRequiredOrderIds,
            boolean requiresReview
    ) {
    }

    public record PgSettlement(String pgOrderId, Money amount) {
    }

    public record RecoveredPayment(
            Long orderId,
            Long projectId,
            String pgOrderId,
            Money paymentAmount,
            boolean paid
    ) {
    }
}
