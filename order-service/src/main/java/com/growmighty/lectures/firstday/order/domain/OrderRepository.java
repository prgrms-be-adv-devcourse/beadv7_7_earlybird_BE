package com.growmighty.lectures.firstday.order.domain;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAll();

    /** 페이지 단위 조회 (정산 데모에서 "조금씩 읽기"에 사용) */
    List<Order> findPage(int page, int size);

    long count();
}
