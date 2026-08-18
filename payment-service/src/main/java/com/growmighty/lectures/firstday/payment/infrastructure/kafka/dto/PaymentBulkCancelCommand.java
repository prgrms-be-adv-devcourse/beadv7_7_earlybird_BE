package com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto;

import java.util.List;

public record PaymentBulkCancelCommand(
    Long refundRequestId,
    List<Long> orderIds,
    String reason
) {
}
