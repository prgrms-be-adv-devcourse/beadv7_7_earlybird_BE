package com.growmighty.lectures.firstday.refund.domain;

import java.util.Arrays;

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

    // 외부 계약의 환불 사유 코드를 내부 ENUM으로 변환
    public static RefundReason fromCode(String code) {
        return Arrays.stream(values())
            .filter(reason -> reason.code.equals(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 환불 사유입니다. reason = " + code));
    }

}
