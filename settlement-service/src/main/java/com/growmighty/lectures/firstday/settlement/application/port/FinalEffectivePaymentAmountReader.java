package com.growmighty.lectures.firstday.settlement.application.port;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.util.List;

public interface FinalEffectivePaymentAmountReader {

    List<Money> findFinalEffectivePaymentAmounts(Long projectId);
}
