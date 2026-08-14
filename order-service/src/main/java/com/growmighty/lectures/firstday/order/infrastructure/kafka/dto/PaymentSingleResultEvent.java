package com.growmighty.lectures.firstday.order.infrastructure.kafka.dto;

public record PaymentSingleResultEvent(
        Long orderId,
        String pgOrderId,
        String status
) {
    public PaymentSingleResultEvent {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId must be positive.");
        }
        if (pgOrderId == null || pgOrderId.isBlank()) {
            throw new IllegalArgumentException("pgOrderId is required.");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required.");
        }
    }
}
