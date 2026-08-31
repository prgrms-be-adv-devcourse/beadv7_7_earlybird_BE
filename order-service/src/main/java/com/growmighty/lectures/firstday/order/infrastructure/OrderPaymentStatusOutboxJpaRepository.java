package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutbox;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxStatus;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPaymentStatusOutboxJpaRepository extends JpaRepository<OrderPaymentStatusOutbox, Long> {
    @Modifying
    @Query(value = """
            insert into order_payment_status_outboxes (
                version, event_id, occurred_at, order_id, pg_order_id, project_id,
                payment_amount, order_status, status, retry_count, next_retry_at,
                created_at, updated_at
            ) values (
                0, :eventId, :occurredAt, :orderId, :pgOrderId, :projectId,
                :paymentAmount, :orderStatus, :status, 0, :nextRetryAt,
                current_timestamp, current_timestamp
            )
            on duplicate key update id = id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId,
                       @Param("occurredAt") Instant occurredAt,
                       @Param("orderId") Long orderId,
                       @Param("pgOrderId") String pgOrderId,
                       @Param("projectId") Long projectId,
                       @Param("paymentAmount") BigDecimal paymentAmount,
                       @Param("orderStatus") String orderStatus,
                       @Param("status") String status,
                       @Param("nextRetryAt") Instant nextRetryAt);

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
