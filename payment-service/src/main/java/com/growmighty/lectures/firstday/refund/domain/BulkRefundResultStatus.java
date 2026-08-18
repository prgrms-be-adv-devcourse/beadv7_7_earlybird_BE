package com.growmighty.lectures.firstday.refund.domain;

import java.util.Arrays;

public enum BulkRefundResultStatus {
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    private final String code;

    BulkRefundResultStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static BulkRefundResultStatus fromCode(String code) {
        return Arrays.stream(values())
            .filter(status -> status.code.equals(code))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 일괄 취소 결과 상태입니다. status = " + code));
    }
}
