package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record TossWebhookPayment(
    @NotBlank String paymentKey
) {
}
