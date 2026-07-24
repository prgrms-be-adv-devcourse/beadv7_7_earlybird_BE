package com.growmighty.lectures.firstday.payment.infrastructure.toss.dto;

public record TossWebhookRequest(
    String eventType,
    TossWebhookPayment data
) {
}
