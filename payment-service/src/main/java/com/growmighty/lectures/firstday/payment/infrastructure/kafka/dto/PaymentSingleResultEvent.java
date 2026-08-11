package com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto;

public record PaymentSingleResultEvent(
    Long orderId,
    String pgOrderId,
    String status
) {
}
