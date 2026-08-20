package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    @Query("select distinct o from Order o left join fetch o.items where o.status in :statuses")
    List<Order> findByStatusInWithItems(@Param("statuses") List<OrderStatus> statuses);

    @Query("""
            select o.id from Order o
            where o.status in :statuses
              and o.updatedAt <= :updatedBefore
            order by o.updatedAt asc, o.id asc
            """)
    List<Long> findRecoveryCandidateIds(
            @Param("statuses") List<OrderStatus> statuses,
            @Param("updatedBefore") LocalDateTime updatedBefore,
            Pageable pageable);

    @Query("select distinct o from Order o left join fetch o.items where o.id in :ids")
    List<Order> findAllByIdInWithItems(@Param("ids") List<Long> ids);

    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItemsForUpdate(@Param("id") Long id);

    @Query("select distinct o from Order o left join fetch o.items where o.userId = :userId and o.orderIdempotencyKey = :orderIdempotencyKey")
    Optional<Order> findByUserIdAndOrderIdempotencyKey(
            @Param("userId") Long userId,
            @Param("orderIdempotencyKey") UUID orderIdempotencyKey);

    @Query("""
            select case when count(oi) > 0 then true else false end
            from OrderItem oi
            where oi.projectId = :projectId
            """)
    boolean existsByProjectId(@Param("projectId") Long projectId);

    @Query("""
        select sum(oi.price.value * oi.quantity)
        from OrderItem oi join oi.order o
        where oi.projectId = :projectId and o.status = com.growmighty.lectures.firstday.order.domain.OrderStatus.PAID
        """)
    Optional<BigDecimal> getFundedAmount(@Param("projectId") Long projectId);

    @Query("""
        select oi from OrderItem oi
        join oi.order o
        where o.userId = :userId
          and oi.rewardId = :rewardId
          and o.status = :status
        """)
    Optional<OrderItem> findPaidItem(
            @Param("userId") Long userId,
            @Param("rewardId") Long rewardId,
            @Param("status") OrderStatus status);

    @Query("""
        select o from Order o
        where o.projectId in :projectIds
          and o.status in :statuses
        order by o.projectId asc, o.id asc
        """)
    List<Order> findByProjectIdsAndStatusIn(
            @Param("projectIds") List<Long> projectIds,
            @Param("statuses") List<OrderStatus> statuses);
}
