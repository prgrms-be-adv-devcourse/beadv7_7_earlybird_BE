package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserId(Long userId);

    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    @Query("""
            select case when count(oi) > 0 then true else false end
            from OrderItem oi
            where oi.projectId = :projectId
            """)
    boolean existsByProjectId(@Param("projectId") Long projectId);

    @Query("""
    select sum(oi.price.value * oi.quantity)
    from OrderItem oi
    where oi.projectId = :projectId
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
}
