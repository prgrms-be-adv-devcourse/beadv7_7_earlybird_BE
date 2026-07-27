package com.growmighty.lectures.firstday.refund.Presentation.dto;

import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;

import java.math.BigDecimal;

public record PaymentRefundResponse(
    Long refundId,
    Long paymentId,
    BigDecimal amount,
    RefundStatus status
) {

    public static PaymentRefundResponse from(Refund refund) {
        return new PaymentRefundResponse(
            refund.getId(),
            refund.getPaymentId(),
            refund.getAmount(),
            refund.getStatus()
        );
    }
}
