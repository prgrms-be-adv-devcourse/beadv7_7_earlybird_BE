package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.AdminProjectReconciliationReviewDetail;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import java.util.List;

public record AdminProjectReconciliationReviewDetailResponse(
        Long projectId,
        String projectName,
        List<PaymentResponse> payments
) {

    public static AdminProjectReconciliationReviewDetailResponse from(AdminProjectReconciliationReviewDetail detail) {
        return new AdminProjectReconciliationReviewDetailResponse(
                detail.projectId(),
                detail.projectName(),
                detail.payments().stream().map(PaymentResponse::from).toList()
        );
    }

    public record PaymentResponse(
            Long orderId,
            String pgOrderId,
            OrderPaymentFact.ReconciliationStatus reconciliationStatus
    ) {

        private static PaymentResponse from(AdminProjectReconciliationReviewDetail.Payment payment) {
            return new PaymentResponse(payment.orderId(), payment.pgOrderId(), payment.reconciliationStatus());
        }
    }
}
