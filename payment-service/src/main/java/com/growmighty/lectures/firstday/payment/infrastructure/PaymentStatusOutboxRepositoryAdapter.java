package com.growmighty.lectures.firstday.payment.infrastructure;


import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentStatusOutboxRepositoryAdapter implements PaymentStatusOutboxRepository {

    private final PaymentStatusOutboxJpaRepository jpaRepository;

    @Override
    public PaymentStatusOutbox save(PaymentStatusOutbox outbox) {
        return jpaRepository.save(outbox);
    }

    @Override
    public boolean existsByPaymentIdAndPaymentStatus(Long paymentId, PaymentStatus paymentStatus) {
        return jpaRepository.existsByPaymentIdAndPaymentStatus(paymentId, paymentStatus);
    }

    @Override
    public List<PaymentStatusOutbox> findPending(int limit) {
        return jpaRepository.findByStatusOrderByIdAsc(
            PaymentStatusOutboxStatus.PENDING,
            PageRequest.of(0, limit)
        );
    }

}
