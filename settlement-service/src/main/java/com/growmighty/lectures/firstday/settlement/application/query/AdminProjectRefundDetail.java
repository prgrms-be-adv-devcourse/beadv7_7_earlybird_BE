package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.time.Instant;
import java.util.List;

public record AdminProjectRefundDetail(
        Long projectId,
        ProjectCancellationReason reason,
        AdminSettlementEntry.RefundPublishStatus publishStatus,
        Instant requestedAt,
        Instant publishedAt,
        AdminSettlementEntry.RefundProcessingStatus processingStatus,
        Instant paymentResultAt,
        List<Payment> payments
) {

    public record Payment(Long orderId, String pgOrderId) {
    }
}
