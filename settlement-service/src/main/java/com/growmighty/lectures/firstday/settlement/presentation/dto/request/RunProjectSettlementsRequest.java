// TODO(settlement-plan): Validate settlementMonth at the HTTP trust boundary and keep scheduling details out of the request.
package com.growmighty.lectures.firstday.settlement.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;

public record RunProjectSettlementsRequest(
        @NotNull YearMonth settlementMonth
) {
}
