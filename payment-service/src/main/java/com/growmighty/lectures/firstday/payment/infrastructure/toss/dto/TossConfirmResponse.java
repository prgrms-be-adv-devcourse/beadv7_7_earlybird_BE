package com.growmighty.lectures.firstday.payment.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossConfirmResponse(
    String paymentKey,
    String orderId,
    BigDecimal totalAmount,
    String status
) {
}
