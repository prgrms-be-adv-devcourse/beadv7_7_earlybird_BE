package com.growmighty.lectures.firstday.product.presentation.dto;

import lombok.NonNull;

public record ChangeStockRequest(@NonNull Integer quantity) {
}
