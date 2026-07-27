package com.growmighty.lectures.firstday.settlement.infrastructure;

import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorPayoutProfileJpaRepository extends JpaRepository<CreatorPayoutProfile, Long> {
}
