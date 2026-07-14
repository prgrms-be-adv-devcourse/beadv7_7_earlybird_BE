package com.growmighty.lectures.firstday.product.presentation.dto;

import lombok.NonNull;

import java.math.BigDecimal;

public record ChangeProductPriceRequest(@NonNull BigDecimal price) {
}
