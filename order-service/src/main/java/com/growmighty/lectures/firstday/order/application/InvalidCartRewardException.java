package com.growmighty.lectures.firstday.order.application;

final class InvalidCartRewardException extends IllegalStateException {
    private final Long rewardId;

    InvalidCartRewardException(Long rewardId, String message, Throwable cause) {
        super(message, cause);
        this.rewardId = rewardId;
    }

    InvalidCartRewardException(Long rewardId, String message) {
        super(message);
        this.rewardId = rewardId;
    }

    Long rewardId() {
        return rewardId;
    }
}
