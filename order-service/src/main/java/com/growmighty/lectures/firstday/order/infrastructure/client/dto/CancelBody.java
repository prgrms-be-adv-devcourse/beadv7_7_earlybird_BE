package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

public record CancelBody(Long orderId, Long paymentId) {
}
