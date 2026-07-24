package com.growmighty.lectures.firstday.order.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderInspectionRequest(@NotNull UUID orderId) {
}
