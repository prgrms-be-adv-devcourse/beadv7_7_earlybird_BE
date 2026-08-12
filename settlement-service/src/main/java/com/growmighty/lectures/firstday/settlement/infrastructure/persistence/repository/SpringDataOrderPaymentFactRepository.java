package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrderPaymentFactRepository extends JpaRepository<OrderPaymentFact, Long> {

    List<OrderPaymentFact> findAllByProjectIdOrderByOrderId(Long projectId);

    @Query("""
            select payment
            from OrderPaymentFact payment
            where payment.status = :status
              and payment.completedAt >= :startInclusive
              and payment.completedAt < :endExclusive
            order by payment.completedAt, payment.orderId
            """)
    List<OrderPaymentFact> findCompletedPaymentsBetween(
            @Param("status") OrderPaymentFact.Status status,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            Pageable pageable
    );
}
