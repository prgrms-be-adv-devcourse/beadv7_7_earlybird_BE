package com.growmighty.lectures.firstday.cart.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(@NotNull Long rewardId, @NotNull @Positive Integer quantity) {
}
