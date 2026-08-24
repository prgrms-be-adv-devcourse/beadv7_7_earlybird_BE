package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentConfirmationTarget;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentConfirmationInProgressException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayFailureType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentApprovalSagaOrchestrator {

    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentGateway paymentGateway;

    public PaymentInfo approve(
        String paymentKey,
        String pgOrderId,
        BigDecimal amount
    ) {
        PaymentConfirmationTarget target;

        try {
            target = paymentConfirmationService.startConfirmation(
                paymentKey,
                pgOrderId,
                amount
            );
        } catch (OptimisticLockingFailureException exception) {
            throw new PaymentConfirmationInProgressException(pgOrderId);
        }

        try {
            PaymentGateway.PgApproval approval = paymentGateway.approve(
                paymentKey,
                target.pgOrderId(),
                target.amount(),
                target.idempotencyKey()
            );

            return paymentConfirmationService.completeConfirmation(
                target.paymentId(),
                paymentKey,
                approval
            );
        } catch (OptimisticLockingFailureException exception) {
            return paymentConfirmationService.findPaidPaymentInfo(target.paymentId())
                .orElseThrow(() -> exception);
        } catch (PaymentGatewayException exception) {
            if (exception.getFailureType() == PaymentGatewayFailureType.DEFINITIVE) {
                paymentConfirmationService.failConfirmation(target.paymentId());
            }

            throw exception;
        }
    }
}
