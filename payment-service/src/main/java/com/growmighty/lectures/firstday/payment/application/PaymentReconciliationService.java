package com.growmighty.lectures.firstday.payment.application;


import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentRecoveryProperties paymentRecoveryProperties;
    private final PaymentStatusOutboxAppender paymentStatusOutboxAppender;

    @Transactional
    public Optional<PaymentInfo> reconcile(PaymentGateway.PgPayment pgPayment) {
        Payment payment = paymentRepository.findByPgOrderId(pgPayment.pgOrderId())
            .orElseThrow(() -> new EntityNotFoundException("paymentKey 에 해당하는 결제가 없습니다."));

        if (!payment.getPaymentKey().value().equals(pgPayment.paymentKey())) {
            throw new IllegalStateException("PG 결제 키가 일치하지 않습니다.");
        }

        switch (pgPayment.status()) {
            case COMPLETED -> {
                payment.validateApproval(
                    pgPayment.paymentKey(),
                    pgPayment.pgOrderId(),
                    pgPayment.amount()
                );

                if (payment.reconcileConfirmed(pgPayment.paymentKey())) {
                    savePaymentAndAppendOutbox(payment);
                }

                return Optional.of(PaymentInfo.from(payment));
            }

            case FAILED, EXPIRED, CANCELLED -> {
                if (payment.reconcileFailed()) {
                    savePaymentAndAppendOutbox(payment);
                }
            }

            case PENDING -> {
                if (payment.failIfConfirmingExpired(
                    LocalDateTime.now(),
                    paymentRecoveryProperties.maximumConfirmingDuration()
                )) {
                    savePaymentAndAppendOutbox(payment);
                }
            }
        }

        return Optional.empty();
    }

    private void savePaymentAndAppendOutbox(Payment payment) {
        paymentRepository.save(payment);
        paymentStatusOutboxAppender.appendIfAbsent(payment);
    }
}
