package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentPrepareRequest(
    @NotNull @Positive Long userId,
    @NotNull @Positive Long orderId,
    @NotNull @Positive BigDecimal amount
    ) {
}
