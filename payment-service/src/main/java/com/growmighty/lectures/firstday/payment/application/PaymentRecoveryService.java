package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    private final PaymentReconciliationService paymentReconciliationService;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusOutboxAppender paymentStatusOutboxAppender;
    private final PaymentRecoveryProperties paymentRecoveryProperties;

    public void recover(Long paymentId) {
        PaymentRecoveryTarget target = getRecoveryTarget(paymentId);
        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(target.paymentKey());

        paymentReconciliationService.reconcile(pgPayment);
    }

    @Transactional
    public void expireReadyPayment(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.failIfReadyExpired(
            LocalDateTime.now(),
            paymentRecoveryProperties.readyTimeOut()
        )) {
            paymentStatusOutboxAppender.savePaymentAndAppendOutbox(payment);
        }
    }

    private PaymentRecoveryTarget getRecoveryTarget(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (!payment.isConfirming()) {
            throw new IllegalStateException(
                "CONFIRMING 상태의 결제만 복구할 수 있습니다. 현재 상태 : " + payment.getStatus()
            );
        }

        if (payment.getPaymentKey() == null) {
            throw new IllegalStateException(
                "CONFIRMING 상태의 결제에 paymentKey가 없습니다. paymentId = " + paymentId
            );
        }

        return new PaymentRecoveryTarget(
            payment.getPaymentId(),
            payment.getPaymentKey().value()
        );
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException(
                "존재하지 않는 결제입니다. paymentId = " + paymentId
            ));
    }
}
