package com.growmighty.lectures.firstday.order.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProjectPaymentsRequest(
        @NotNull @Min(1) @Max(12) Integer projectMonth
) {
}
