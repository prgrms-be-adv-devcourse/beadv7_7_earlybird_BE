package com.growmighty.lectures.firstday.payment.domain.vo;

public record SensitiveValue(String value) {

    public SensitiveValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("민감 값은 필수입니다.");
        }
    }
}
