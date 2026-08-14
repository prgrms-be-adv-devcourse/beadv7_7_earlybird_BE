package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectPayoutRunRepository extends JpaRepository<ProjectPayoutRun, Long> {

    Optional<ProjectPayoutRun> findByActiveRunMonth(String activeRunMonth);
}
