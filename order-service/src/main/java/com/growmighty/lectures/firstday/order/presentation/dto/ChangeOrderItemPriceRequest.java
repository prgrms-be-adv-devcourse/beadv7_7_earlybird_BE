package com.growmighty.lectures.firstday.order.presentation.dto;

import lombok.NonNull;

import java.math.BigDecimal;

public record ChangeOrderItemPriceRequest(@NonNull BigDecimal price) {
}
