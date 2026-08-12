package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.CartCleanupOutbox;
import com.growmighty.lectures.firstday.order.domain.CartCleanupStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartCleanupOutboxJpaRepository extends JpaRepository<CartCleanupOutbox, Long> {
    boolean existsByOrderId(Long orderId);

    Optional<CartCleanupOutbox> findByOrderId(Long orderId);

    @Query("""
            select o.id from CartCleanupOutbox o
            where o.status = :status and o.nextRetryAt <= :now
            order by o.nextRetryAt asc, o.id asc
            """)
    List<Long> findPendingIds(@Param("status") CartCleanupStatus status,
                              @Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CartCleanupOutbox o
            set o.nextRetryAt = :leaseUntil, o.version = o.version + 1
            where o.id = :id and o.status = :status and o.nextRetryAt <= :now
            """)
    int claim(@Param("id") Long id, @Param("status") CartCleanupStatus status,
              @Param("now") LocalDateTime now, @Param("leaseUntil") LocalDateTime leaseUntil);
}
