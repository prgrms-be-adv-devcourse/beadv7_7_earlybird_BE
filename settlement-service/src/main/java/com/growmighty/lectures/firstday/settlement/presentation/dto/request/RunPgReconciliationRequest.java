package com.growmighty.lectures.firstday.settlement.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;

public record RunPgReconciliationRequest(
        @NotNull YearMonth settlementMonth
) {
}
