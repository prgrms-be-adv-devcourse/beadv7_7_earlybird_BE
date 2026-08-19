package com.growmighty.lectures.firstday.refund.domain;

public record BulkRefundOrder(
    Long refundRequestId,
    Long orderId,
    RefundStatus refundStatus
) {
}
