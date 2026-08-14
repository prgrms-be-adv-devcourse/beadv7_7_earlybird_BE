package com.growmighty.lectures.firstday.order.infrastructure;

import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutbox;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.order.domain.OrderPaymentStatusOutboxStatus;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderPaymentStatusOutboxRepositoryAdapter implements OrderPaymentStatusOutboxRepository {
    private final OrderPaymentStatusOutboxJpaRepository jpaRepository;

    @Override
    public OrderPaymentStatusOutbox save(OrderPaymentStatusOutbox outbox) {
        return jpaRepository.save(outbox);
    }

    @Override
    public boolean existsByOrderIdAndOrderStatus(Long orderId, OrderStatus orderStatus) {
        return jpaRepository.existsByOrderIdAndOrderStatus(orderId, orderStatus);
    }

    @Override
    public Optional<OrderPaymentStatusOutbox> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<OrderPaymentStatusOutbox> findByOrderIdAndOrderStatus(Long orderId, OrderStatus orderStatus) {
        return jpaRepository.findByOrderIdAndOrderStatus(orderId, orderStatus);
    }

    @Override
    public boolean existsEarlierPending(Long orderId, Long id) {
        return jpaRepository.existsByOrderIdAndStatusAndIdLessThan(
                orderId, OrderPaymentStatusOutboxStatus.PENDING, id);
    }

    @Override
    public List<Long> findPendingIds(Instant now, int batchSize) {
        return jpaRepository.findPendingIds(
                OrderPaymentStatusOutboxStatus.PENDING, now, PageRequest.of(0, batchSize));
    }

    @Override
    public boolean claim(Long id, Instant now, Instant leaseUntil) {
        return jpaRepository.claim(id, OrderPaymentStatusOutboxStatus.PENDING, now, leaseUntil) == 1;
    }
}
