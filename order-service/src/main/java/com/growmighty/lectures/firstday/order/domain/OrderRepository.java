package com.growmighty.lectures.firstday.order.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Order saveAndFlush(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdWithItems(Long id);

    boolean existsByProjectId(Long projectId);

    Optional<BigDecimal> getFundedAmount(Long projectId);

    Optional<OrderItem> findPaidItem(Long userId, Long rewardId);

    List<Order> findAll();

    List<Order> findByUserId(Long userId);

    /** 페이지 단위 조회 (정산 데모에서 "조금씩 읽기"에 사용) */
    List<Order> findPage(int page, int size);

    long count();
}
