package com.growmighty.lectures.firstday.order.presentation.dto;

import lombok.NonNull;

public record ChangeOrderItemQuantityRequest(@NonNull Integer quantity) {
}
