package com.growmighty.lectures.firstday.order.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record OrderInspectionRequest(@NotNull Long orderId) {
}
