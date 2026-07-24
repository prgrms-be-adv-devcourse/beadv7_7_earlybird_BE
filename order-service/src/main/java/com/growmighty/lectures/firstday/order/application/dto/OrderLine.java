package com.growmighty.lectures.firstday.order.application.dto;

import java.math.BigDecimal;

public record OrderLine(Long rewardId, int quantity, BigDecimal expectedUnitPrice) {
}
