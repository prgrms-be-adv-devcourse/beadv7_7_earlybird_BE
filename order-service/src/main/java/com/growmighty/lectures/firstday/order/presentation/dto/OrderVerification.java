package com.growmighty.lectures.firstday.order.presentation.dto;

import com.growmighty.lectures.firstday.order.application.dto.OrderVerificationResult;

public record OrderVerification(
        boolean verified,
        String rewardName
) {
    public static OrderVerification from(OrderVerificationResult result) {
        return new OrderVerification(result.verified(), result.rewardName());
    }
}
