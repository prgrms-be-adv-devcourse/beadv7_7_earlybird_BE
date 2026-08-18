package com.growmighty.lectures.firstday.refund.domain;

public record BulkRefundOrder(
    Long settlementId,
    Long orderId
) {
}
