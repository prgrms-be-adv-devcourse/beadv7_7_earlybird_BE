package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TossWebhookRequest(
    @NotBlank String eventType,
    @NotNull @Valid TossWebhookPayment data
) {
}
