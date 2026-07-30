package com.growmighty.lectures.firstday.payment.infrastructure;

import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentStatusOutboxJpaRepository extends JpaRepository<PaymentStatusOutbox, Long> {

    List<PaymentStatusOutbox> findByStatusOrderByIdAsc(PaymentStatusOutboxStatus status, Pageable pageable);

    boolean existsByPaymentIdAndPaymentStatus(Long paymentId, PaymentStatus paymentStatus);
}
