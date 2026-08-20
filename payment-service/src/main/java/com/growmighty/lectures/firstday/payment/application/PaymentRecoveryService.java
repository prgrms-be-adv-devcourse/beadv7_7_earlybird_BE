package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentReconciliationService paymentReconciliationService;
    private final PaymentGateway paymentGateway;

    public void recover(Long paymentId) {
        PaymentRecoveryTarget target = paymentConfirmationService.getRecoveryTarget(paymentId);
        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(target.paymentKey());

        paymentReconciliationService.reconcile(pgPayment);
    }

    public void expireReadyPayment(Long paymentId) {
        paymentConfirmationService.expireReadyPayment(paymentId);
    }
}
