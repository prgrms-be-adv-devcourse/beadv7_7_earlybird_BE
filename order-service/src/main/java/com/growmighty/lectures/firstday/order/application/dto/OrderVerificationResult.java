package com.growmighty.lectures.firstday.order.application.dto;

public record OrderVerificationResult(
        boolean verified,
        String rewardName
) {
    public static OrderVerificationResult verified(String rewardName) {
        return new OrderVerificationResult(true, rewardName);
    }

    public static OrderVerificationResult unverified() {
        return new OrderVerificationResult(false, "");
    }
}
