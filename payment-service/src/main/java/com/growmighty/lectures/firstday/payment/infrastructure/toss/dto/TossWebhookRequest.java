package com.growmighty.lectures.firstday.payment.infrastructure.toss.dto;

import org.apache.commons.codec.StringEncoder;

public record TossWebhookRequest(
    StringEncoder eventType,
    TossWebhookPayment data
) {
}
