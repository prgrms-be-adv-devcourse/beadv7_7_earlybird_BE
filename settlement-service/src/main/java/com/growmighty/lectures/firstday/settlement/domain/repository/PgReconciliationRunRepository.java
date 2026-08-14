package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import java.time.YearMonth;
import java.util.Optional;

public interface PgReconciliationRunRepository {

    PgReconciliationRun save(PgReconciliationRun run);

    Optional<PgReconciliationRun> findRunningBySettlementMonth(YearMonth settlementMonth);
}
