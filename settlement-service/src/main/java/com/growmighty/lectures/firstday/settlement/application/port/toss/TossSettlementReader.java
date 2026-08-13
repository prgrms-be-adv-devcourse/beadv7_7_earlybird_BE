package com.growmighty.lectures.firstday.settlement.application.port.toss;

import java.util.List;

public interface TossSettlementReader {

    List<TossSettlement> find(TossSettlementQuery query);
}
