package com.growmighty.lectures.firstday.order.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record PaymentStatusRequest(
        @NotBlank String status
) {
}
