package com.growmighty.lectures.firstday.settlement.application.port.toss;

import java.time.LocalDate;
import java.util.Objects;

public record TossSettlementQuery(
        LocalDate startDate,
        LocalDate endDate,
        DateType dateType,
        int page,
        int size
) {

    public static final int MAX_SIZE = 5_000;

    public TossSettlementQuery {
        Objects.requireNonNull(startDate, "토스 정산 조회 시작일은 필수입니다.");
        Objects.requireNonNull(endDate, "토스 정산 조회 종료일은 필수입니다.");
        Objects.requireNonNull(dateType, "토스 정산 조회 기준일은 필수입니다.");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("토스 정산 조회 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (page < 1) {
            throw new IllegalArgumentException("토스 정산 조회 page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("토스 정산 조회 size는 1에서 5000 사이여야 합니다.");
        }
    }

    public enum DateType {
        SOLD_DATE,
        PAID_OUT_DATE
    }
}
