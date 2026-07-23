package com.growmighty.lectures.firstday.settlement.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record RunProjectSettlementsCommand(
        YearMonth settlementMonth,
        LocalDate scheduledDate,
        LocalDateTime confirmedAt
) {
}
