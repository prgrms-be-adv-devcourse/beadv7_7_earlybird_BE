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
    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusOutboxAppender paymentStatusOutboxAppender;
    private final PaymentRecoveryProperties paymentRecoveryProperties;

    public void recover(Long paymentId) {
        PaymentRecoveryTarget target = paymentConfirmationService.getRecoveryTarget(paymentId);
        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(target.paymentKey());

        paymentReconciliationService.reconcile(pgPayment);
    }

    @Transactional
    public void expireReadyPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new EntityNotFoundException(
                "존재하지 않는 결제입니다. paymentId = " + paymentId
            )); // <-- import : EntityNotFoundException, Payment

        if (payment.failIfReadyExpired(
            LocalDateTime.now(), // <-- import : LocalDateTime
            paymentRecoveryProperties.readyTimeOut()
        )) {
            paymentRepository.save(payment);
            paymentStatusOutboxAppender.appendIfAbsent(payment);
        }
    }
}
