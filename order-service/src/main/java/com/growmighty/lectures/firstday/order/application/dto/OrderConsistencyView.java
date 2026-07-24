package com.growmighty.lectures.firstday.order.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderConsistencyView(
        UUID orderId,
        BigDecimal storedTotal,
        BigDecimal recalculatedTotal,
        boolean consistent
) {
}
