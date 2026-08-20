package com.growmighty.lectures.firstday.settlement.application.query;

import java.util.List;

public interface AdminSettlementEntryRepository {

    List<AdminSettlementEntry> findAll(AdminSettlementSort sort);
}
