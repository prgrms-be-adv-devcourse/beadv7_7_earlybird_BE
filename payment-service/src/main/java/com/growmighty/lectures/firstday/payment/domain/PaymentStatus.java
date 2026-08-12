package com.growmighty.lectures.firstday.payment.domain;

public enum PaymentStatus {
    READY("READY"),
    CONFIRMING("CONFIRMING"),
    PAID("PAID"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED");

    private final String code;

    PaymentStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
