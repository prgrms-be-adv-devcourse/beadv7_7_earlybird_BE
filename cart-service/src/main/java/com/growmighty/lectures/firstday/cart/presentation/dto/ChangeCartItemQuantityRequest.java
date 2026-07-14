package com.growmighty.lectures.firstday.cart.presentation.dto;

import lombok.NonNull;

public record ChangeCartItemQuantityRequest(@NonNull Integer quantity) {
}
