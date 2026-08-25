package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import com.growmighty.lectures.firstday.settlement.application.query.AdminProjectRefundDetail;
import com.growmighty.lectures.firstday.settlement.application.query.AdminSettlementEntry;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectCancellationReason;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record AdminProjectRefundDetailResponse(
        Long refundRequestId,
        Long projectId,
        String projectName,
        ProjectCancellationReason reason,
        AdminSettlementEntry.RefundStatus refundStatus,
        OffsetDateTime requestedAt,
        OffsetDateTime paymentResultAt,
        List<PaymentResponse> payments
) {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static AdminProjectRefundDetailResponse from(AdminProjectRefundDetail detail) {
        return new AdminProjectRefundDetailResponse(
                detail.refundRequestId(),
                detail.projectId(),
                detail.projectName(),
                detail.reason(),
                detail.refundStatus(),
                detail.requestedAt().atZone(SEOUL).toOffsetDateTime(),
                toSeoul(detail.paymentResultAt()),
                detail.payments().stream().map(PaymentResponse::from).toList()
        );
    }

    public record PaymentResponse(Long orderId, String pgOrderId, boolean actionRequired) {

        private static PaymentResponse from(AdminProjectRefundDetail.Payment payment) {
            return new PaymentResponse(payment.orderId(), payment.pgOrderId(), payment.actionRequired());
        }
    }

    private static OffsetDateTime toSeoul(java.time.Instant instant) {
        return instant == null ? null : instant.atZone(SEOUL).toOffsetDateTime();
    }
}
