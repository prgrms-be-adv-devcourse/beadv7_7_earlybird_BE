package com.growmighty.lectures.firstday.refund.Presentation.dto;

import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import jakarta.validation.constraints.NotNull;

public record PaymentRefundRequest(
    @NotNull RefundReason reason
    ) {
}
