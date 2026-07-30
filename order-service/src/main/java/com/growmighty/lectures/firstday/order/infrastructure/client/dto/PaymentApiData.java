package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record PaymentApiData(
        Long paymentId,
        String pgOrderId,
        BigDecimal amount,
        String status
) {
}
