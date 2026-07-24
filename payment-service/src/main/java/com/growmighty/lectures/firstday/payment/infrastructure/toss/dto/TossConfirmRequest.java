package com.growmighty.lectures.firstday.payment.infrastructure.toss.dto;

import java.math.BigDecimal;

public record TossConfirmRequest(
    String paymentKey,
    String orderId,
    BigDecimal amount
) {
}
