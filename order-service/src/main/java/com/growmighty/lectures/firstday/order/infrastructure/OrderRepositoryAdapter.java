package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
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
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Order> findByIdWithItems(UUID id) {
        return jpaRepository.findByIdWithItems(id);
    }

    @Override
    public boolean existsByProjectId(Long projectId) {
        return jpaRepository.existsByProjectId(projectId);
    }

    @Override
    public Optional<BigDecimal> getFundedAmount(Long projectId) {return jpaRepository.getFundedAmount(projectId);}

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
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
