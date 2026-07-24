package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentGateway paymentGateway;
    private final PaymentRecoveryProperties paymentRecoveryProperties;

    public void recover(Long paymentId) {
        PaymentRecoveryTarget target = paymentConfirmationService.getRecoveryTarget(paymentId);
        PaymentGateway.PgPayment payment = paymentGateway.getPayment(target.paymentKey());

        switch (payment.status()) {
            case COMPLETED -> paymentConfirmationService.completeConfirmation(
                target.paymentId(),
                target.paymentKey(),
                new PaymentGateway.PgApproval(
                    payment.paymentKey(),
                    payment.pgOrderId(),
                    payment.amount()
                )
            );

            case FAILED, EXPIRED, CANCELLED -> paymentConfirmationService.failConfirmation(target.paymentId());

            case PENDING -> paymentConfirmationService.failConfirmationIfExpired(
                target.paymentId(),
                LocalDateTime.now(),
                paymentRecoveryProperties.maximumConfirmingDuration()
            );
        }
    }
}
