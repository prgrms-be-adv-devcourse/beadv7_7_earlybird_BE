package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);
}
