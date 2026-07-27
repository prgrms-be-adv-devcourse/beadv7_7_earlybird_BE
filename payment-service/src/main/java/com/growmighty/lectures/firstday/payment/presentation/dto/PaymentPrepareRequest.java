package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentPrepareRequest(
    @NotNull UUID orderId,
    @NotNull @Positive BigDecimal amount
    ) {
}
