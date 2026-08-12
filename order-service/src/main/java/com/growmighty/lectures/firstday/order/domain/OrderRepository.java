package com.growmighty.lectures.firstday.order.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);

    Order saveAndFlush(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdWithItems(Long id);

    Optional<Order> findByUserIdAndOrderIdempotencyKey(Long userId, UUID orderIdempotencyKey);

    boolean existsByProjectId(Long projectId);

    Optional<BigDecimal> getFundedAmount(Long projectId);

    Optional<OrderItem> findPaidItem(Long userId, Long rewardId);

    List<Order> findAll();

    List<Order> findByUserId(Long userId);

    List<Order> findByStatusIn(List<OrderStatus> statuses);

    List<Order> findByProjectIdsAndStatusIn(List<Long> projectIds, List<OrderStatus> statuses);

    /** 페이지 단위 조회 (정산 데모에서 "조금씩 읽기"에 사용) */
    List<Order> findPage(int page, int size);

    long count();
}
