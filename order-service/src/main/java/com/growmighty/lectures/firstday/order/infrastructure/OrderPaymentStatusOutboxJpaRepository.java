package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutbox;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxStatus;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentStatusOutboxJpaRepository extends JpaRepository<OrderPaymentStatusOutbox, Long> {
    boolean existsByOrderIdAndOrderStatus(Long orderId, OrderStatus orderStatus);

    Optional<OrderPaymentStatusOutbox> findByOrderIdAndOrderStatus(Long orderId, OrderStatus orderStatus);

    boolean existsByOrderIdAndStatusAndIdLessThan(
            Long orderId, OrderPaymentStatusOutboxStatus status, Long id);

    @Query("""
            select o.id from OrderPaymentStatusOutbox o
            where o.status = :status and o.nextRetryAt <= :now
            order by o.nextRetryAt asc, o.id asc
            """)
    List<Long> findPendingIds(@Param("status") OrderPaymentStatusOutboxStatus status,
                              @Param("now") Instant now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OrderPaymentStatusOutbox o
            set o.nextRetryAt = :leaseUntil, o.version = o.version + 1
            where o.id = :id and o.status = :status and o.nextRetryAt <= :now
            """)
    int claim(@Param("id") Long id, @Param("status") OrderPaymentStatusOutboxStatus status,
              @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
