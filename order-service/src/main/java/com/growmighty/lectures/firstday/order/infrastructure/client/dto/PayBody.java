package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayBody(UUID orderId, BigDecimal amount) {
}
