// TODO(settlement-plan): Keep settlementMonth as the run key and derive schedule time consistently from the injected Clock.
package com.growmighty.lectures.firstday.settlement.application.run;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record RunProjectSettlementsCommand(
        YearMonth settlementMonth,
        LocalDate scheduledDate,
        LocalDateTime confirmedAt
) {
}
