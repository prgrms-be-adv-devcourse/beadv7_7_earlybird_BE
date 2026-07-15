package com.growmighty.lectures.firstday.payment.presentation.dto;

import lombok.NonNull;

import java.math.BigDecimal;

public record PayRequest(@NonNull Long orderId, @NonNull BigDecimal amount) {
}
