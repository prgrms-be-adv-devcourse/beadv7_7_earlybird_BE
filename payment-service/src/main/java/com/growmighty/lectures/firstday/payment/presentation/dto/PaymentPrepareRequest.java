package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentPrepareRequest(
    @NotNull @Positive Long orderId,
    @NotNull @Positive Long userId,
    @NotBlank String pgOrderId,
    @NotNull @Positive BigDecimal amount
    ) {
}
