package com.growmighty.lectures.firstday.settlement.application.port.order;

import java.time.YearMonth;
import java.util.Set;

public interface OrderPaymentRecoveryReader {

    OrderPaymentRecovery recover(Set<Long> projectIds, YearMonth settlementMonth);
}
