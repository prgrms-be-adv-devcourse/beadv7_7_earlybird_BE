package com.growmighty.lectures.firstday.order.application.port;

import java.math.BigDecimal;

public interface ProjectFundedAmountPort {
    void updateFundedAmount(Long projectId, BigDecimal fundedAmount);
}
