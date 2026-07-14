package com.growmighty.lectures.firstday.settlement.read;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {
    private final OrderJpaRepository jpaRepository;

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll();
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
