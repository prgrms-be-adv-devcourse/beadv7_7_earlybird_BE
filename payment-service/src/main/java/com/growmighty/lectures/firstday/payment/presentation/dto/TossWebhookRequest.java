package com.growmighty.lectures.firstday.payment.presentation.dto;

import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossWebhookPayment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TossWebhookRequest(
    @NotBlank String eventType,
    @NotNull @Valid TossWebhookPayment data
) {
}
