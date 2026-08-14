package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPgReconciliationRunRepository extends JpaRepository<PgReconciliationRun, Long> {

    Optional<PgReconciliationRun> findByActiveRunMonth(String activeRunMonth);
}
