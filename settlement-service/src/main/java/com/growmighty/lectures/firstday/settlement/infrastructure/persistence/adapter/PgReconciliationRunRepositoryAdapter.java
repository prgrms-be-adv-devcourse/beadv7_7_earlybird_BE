package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import com.growmighty.lectures.firstday.settlement.domain.repository.PgReconciliationRunRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataPgReconciliationRunRepository;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PgReconciliationRunRepositoryAdapter implements PgReconciliationRunRepository {

    private final SpringDataPgReconciliationRunRepository repository;

    @Override
    @Transactional
    public PgReconciliationRun save(PgReconciliationRun run) {
        return repository.saveAndFlush(run);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PgReconciliationRun> findRunningBySettlementMonth(YearMonth settlementMonth) {
        return repository.findByActiveRunMonth(settlementMonth.toString());
    }
}
