package com.growmighty.lectures.firstday.payment.application.dto;

public record PaymentSingleResultEvent(
    Long orderId,
    String pgOrderId,
    String status
) {
}
