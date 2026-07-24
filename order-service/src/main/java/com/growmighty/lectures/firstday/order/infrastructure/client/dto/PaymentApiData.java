package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentApiData(
        Long paymentId,
        UUID orderId,
        BigDecimal amount,
        String status
) {
}
