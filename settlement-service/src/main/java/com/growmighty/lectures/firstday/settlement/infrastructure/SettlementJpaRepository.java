package com.growmighty.lectures.firstday.settlement.infrastructure;

import com.growmighty.lectures.firstday.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementJpaRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
