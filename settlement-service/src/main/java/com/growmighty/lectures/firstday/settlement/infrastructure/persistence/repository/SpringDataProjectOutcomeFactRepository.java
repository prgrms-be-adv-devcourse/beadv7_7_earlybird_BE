package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProjectOutcomeFactRepository extends JpaRepository<ProjectOutcomeFact, Long> {
}
