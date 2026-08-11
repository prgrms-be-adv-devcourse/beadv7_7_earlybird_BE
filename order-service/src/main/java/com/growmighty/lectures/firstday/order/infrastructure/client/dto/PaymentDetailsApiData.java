package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record PaymentDetailsApiData(
        Long paymentId,
        Long orderId,
        String pgOrderId,
        BigDecimal amount,
        String status
) {
}
