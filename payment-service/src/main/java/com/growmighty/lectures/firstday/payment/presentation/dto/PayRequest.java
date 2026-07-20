package com.growmighty.lectures.firstday.payment.presentation.dto;

import lombok.NonNull;

public record PayRequest(@NonNull String paymentKey, @NonNull String pgOrderId) {

}
