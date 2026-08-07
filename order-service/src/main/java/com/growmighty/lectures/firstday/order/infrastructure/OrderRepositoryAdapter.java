package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {
    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Order saveAndFlush(Order order) {
        return jpaRepository.saveAndFlush(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithItems(Long id) {
        return jpaRepository.findByIdWithItems(id);
    }

    @Override
    public Optional<Order> findByUserIdAndOrderIdempotencyKey(Long userId, UUID orderIdempotencyKey) {
        return jpaRepository.findByUserIdAndOrderIdempotencyKey(userId, orderIdempotencyKey);
    }

    @Override
    public boolean existsByProjectId(Long projectId) {
        return jpaRepository.existsByProjectId(projectId);
    }

    @Override
    public Optional<BigDecimal> getFundedAmount(Long projectId) {return jpaRepository.getFundedAmount(projectId);}

    @Override
    public Optional<OrderItem> findPaidItem(Long userId, Long rewardId) {
        return jpaRepository.findPaidItem(userId, rewardId, OrderStatus.PAID);
    }

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public List<Order> findByStatusIn(List<OrderStatus> statuses) {
        return jpaRepository.findByStatusInWithItems(statuses);
    }

    @Override
    public List<Order> findPage(int page, int size) {
        return jpaRepository.findAll(PageRequest.of(page, size)).getContent();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }
}
