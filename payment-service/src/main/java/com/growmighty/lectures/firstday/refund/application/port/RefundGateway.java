package com.growmighty.lectures.firstday.refund.application.port;

import com.growmighty.lectures.firstday.refund.domain.RefundReason;

public interface RefundGateway {
    void refund(String paymentKey, RefundReason refundReason, String cancelIdempotencyKey);
}
