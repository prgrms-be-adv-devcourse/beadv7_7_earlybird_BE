package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {
    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentGateway paymentGateway;

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

            case FAILED, EXPIRED -> paymentConfirmationService.failConfirmation(target.paymentId());

            case PENDING, CANCELLED -> {
                // Toss 처리가 아직 끝나지 않았거나, 로컬 상태와 맞지 않는 경우
                // CONFIRMING 상태를 유지하고 이후 다시 조회
            }
        }
    }
}
