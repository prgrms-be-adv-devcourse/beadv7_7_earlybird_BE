package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderPaymentFactRepository extends JpaRepository<OrderPaymentFact, Long> {

    List<OrderPaymentFact> findAllByProjectIdOrderByOrderId(Long projectId);
}
