package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import java.util.List;

public record AdminProjectReconciliationReviewDetail(
        Long projectId,
        String projectName,
        List<Payment> payments
) {

    public record Payment(
            Long orderId,
            String pgOrderId,
            OrderPaymentFact.ReconciliationStatus reconciliationStatus
    ) {
    }
}
