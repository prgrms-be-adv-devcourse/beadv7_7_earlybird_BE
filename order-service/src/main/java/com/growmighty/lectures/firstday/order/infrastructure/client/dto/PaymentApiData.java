package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record PaymentApiData(
        Long paymentId,
        BigDecimal amount,
        String status
) {
}
