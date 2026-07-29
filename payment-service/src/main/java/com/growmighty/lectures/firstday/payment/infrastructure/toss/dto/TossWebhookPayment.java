package com.growmighty.lectures.firstday.payment.infrastructure.toss.dto;

import jakarta.validation.constraints.NotBlank;

public record TossWebhookPayment(
    @NotBlank String paymentKey
) {
}
