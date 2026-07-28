package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.List;

public interface LegacyProjectSettlementOriginalStore {

    List<LegacyProjectSettlementOriginal> findAll();

    void backfillAndEnforceRequiredOriginals(List<ResolvedProjectSettlementOriginal> originals);
}
