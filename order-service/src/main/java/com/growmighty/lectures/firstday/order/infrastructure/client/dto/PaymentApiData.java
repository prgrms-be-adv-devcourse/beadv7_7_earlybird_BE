package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record PaymentApiData(
        Long paymentId,
        String orderId,
        BigDecimal amount,
        String status
) {
}
