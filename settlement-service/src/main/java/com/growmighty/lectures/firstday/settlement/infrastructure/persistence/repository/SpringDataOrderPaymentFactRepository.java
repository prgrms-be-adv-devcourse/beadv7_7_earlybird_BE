package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataOrderPaymentFactRepository extends JpaRepository<OrderPaymentFact, Long> {

    List<OrderPaymentFact> findAllByProjectIdOrderByOrderId(Long projectId);

    List<OrderPaymentFact> findAllByProjectIdAndStatusOrderByOrderId(
            Long projectId,
            OrderPaymentFact.Status status
    );

    @Query("""
            select payment from OrderPaymentFact payment
            where payment.status = com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact.Status.COMPLETED
              and payment.completedAt >= :startInclusive
              and payment.completedAt < :endExclusive
            order by payment.completedAt asc, payment.orderId asc
            """)
    List<OrderPaymentFact> findCompletedInRange(
            Instant startInclusive,
            Instant endExclusive,
            Pageable pageable
    );
}
