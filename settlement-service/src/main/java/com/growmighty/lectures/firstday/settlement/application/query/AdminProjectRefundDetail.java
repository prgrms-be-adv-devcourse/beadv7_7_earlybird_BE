package com.growmighty.lectures.firstday.settlement.application.query;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.time.Instant;
import java.util.List;

public record AdminProjectRefundDetail(
        Long refundRequestId,
        Long projectId,
        String projectName,
        ProjectCancellationReason reason,
        AdminSettlementEntry.RefundStatus refundStatus,
        Instant requestedAt,
        Instant paymentResultAt,
        List<Payment> payments
) {

    public record Payment(Long orderId, String pgOrderId, boolean actionRequired) {
    }
}
