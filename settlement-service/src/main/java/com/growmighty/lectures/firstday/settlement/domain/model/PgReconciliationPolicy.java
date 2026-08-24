package com.growmighty.lectures.firstday.settlement.domain.model;

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
        Map<String, Money> paymentAmounts = paymentAmounts(payments);
        Map<String, Money> settlementAmounts = settlementAmounts(settlements);
        return paymentAmounts != null && paymentAmounts.equals(settlementAmounts);
    }

    public static boolean matchesRecoveredPayments(
            List<OrderPaymentFact> payments,
            List<RecoveredPayment> recoveredPayments
    ) {
        if (payments.size() != recoveredPayments.size()) {
            return false;
        }
        Map<Long, OrderPaymentFact> paymentsByOrderId = new HashMap<>();
        for (OrderPaymentFact payment : payments) {
            if (paymentsByOrderId.put(payment.orderId(), payment) != null) {
                return false;
            }
        }
        for (RecoveredPayment recovered : recoveredPayments) {
            OrderPaymentFact payment = paymentsByOrderId.remove(recovered.orderId());
            if (payment == null
                    || !payment.projectId().equals(recovered.projectId())
                    || !payment.pgOrderId().equals(recovered.pgOrderId())
                    || !payment.paymentAmount().equals(recovered.paymentAmount())
                    || !recovered.paid()) {
                return false;
            }
        }
        return paymentsByOrderId.isEmpty();
    }

    private static Map<String, Money> paymentAmounts(List<OrderPaymentFact> payments) {
        Map<String, Money> amounts = new HashMap<>();
        for (OrderPaymentFact payment : payments) {
            if (amounts.put(payment.pgOrderId(), payment.paymentAmount()) != null) {
                return null;
            }
        }
        return amounts;
    }

    private static Map<String, Money> settlementAmounts(List<PgSettlement> settlements) {
        Map<String, Money> amounts = new HashMap<>();
        for (PgSettlement settlement : settlements) {
            if (amounts.put(settlement.pgOrderId(), settlement.amount()) != null) {
                return null;
            }
        }
        return amounts;
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
