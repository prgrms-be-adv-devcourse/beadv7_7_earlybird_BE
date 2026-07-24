package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentGateway paymentGateway;
    private final PaymentRecoveryProperties paymentRecoveryProperties;

    public void recover(Long paymentId) {
        PaymentRecoveryTarget target = paymentConfirmationService.getRecoveryTarget(paymentId);
        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(target.paymentKey());

        paymentConfirmationService.reconcile(pgPayment);
    }
}
