package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.CreatorPayoutProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCreatorPayoutProfileRepository
        extends JpaRepository<CreatorPayoutProfileJpaEntity, Long> {
}
