package com.growmighty.lectures.firstday.settlement.application.port.order;

import java.time.YearMonth;

public interface OrderPaymentRecoveryReader {

    OrderPaymentRecovery recover(YearMonth settlementMonth);
}
