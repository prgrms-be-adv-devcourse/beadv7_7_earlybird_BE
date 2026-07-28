package com.growmighty.lectures.firstday.settlement.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

public final class PayoutSchedulePolicy {

    private PayoutSchedulePolicy() {
    }

    public static PayoutSchedulePolicy current() {
        return new PayoutSchedulePolicy();
    }

    public LocalDate scheduledDateFor(YearMonth settlementMonth) {
        LocalDate scheduledDate = Objects.requireNonNull(
                settlementMonth,
                "프로젝트 정산 대상 월은 필수입니다."
        ).plusMonths(1).atDay(1);

        while (scheduledDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || scheduledDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            scheduledDate = scheduledDate.plusDays(1);
        }
        return scheduledDate;
    }
}
