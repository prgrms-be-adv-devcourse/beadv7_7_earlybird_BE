package com.growmighty.lectures.firstday.order.infrastructure.client.dto;

import java.math.BigDecimal;

public record PayBody(Long orderId, Long userId, BigDecimal amount) {
}
