package com.growmighty.lectures.firstday.settlement.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;

public record RunProjectSettlementsRequest(
        @NotNull YearMonth settlementMonth
) {
}
