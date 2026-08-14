package com.growmighty.lectures.firstday.order.application.dto;

import com.growmighty.lectures.firstday.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPaymentStatusMessage(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        String pgOrderId,
        Long projectId,
        BigDecimal paymentAmount,
        OrderStatus orderStatus
) {
}
