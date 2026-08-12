package com.growmighty.lectures.firstday.refund.domain;

public enum RefundReason {
    GOAL_FAILED("GOAL_FAILED"),
    USER_CANCEL("USER_CANCEL");

    private final String code;

    RefundReason(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
