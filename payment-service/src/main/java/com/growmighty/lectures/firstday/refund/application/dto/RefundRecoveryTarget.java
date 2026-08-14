package com.growmighty.lectures.firstday.refund.application.dto;

import com.growmighty.lectures.firstday.payment.domain.vo.SensitiveValue;

public record RefundRecoveryTarget(
    Long refundId,
    SensitiveValue paymentKey
) {
}
