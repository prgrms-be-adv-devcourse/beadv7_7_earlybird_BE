package com.growmighty.lectures.firstday.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record PayRequest(@NotBlank String paymentKey, @NotBlank String pgOrderId) {

}
