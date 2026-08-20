package com.growmighty.lectures.firstday.payment.application.dto;

public record PaymentStatusChangedEvent(
    Long orderId,
    String pgOrderId,
    String status
) {
}
