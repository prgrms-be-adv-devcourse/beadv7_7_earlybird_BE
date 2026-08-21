package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentCancelRequest(
    @NotNull @Positive Long orderId,
    @NotNull @Positive Long paymentId
) {
}
