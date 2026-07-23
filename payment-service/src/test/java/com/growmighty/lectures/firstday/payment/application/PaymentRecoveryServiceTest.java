package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String PG_ORDER_ID = "order-1";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private PaymentRecoveryService paymentRecoveryService;

    @Test
    void 토스_결제가_완료되었으면_결제를_완료_처리한다() {
        when(paymentConfirmationService.getRecoveryTarget(PAYMENT_ID)).thenReturn(recoveryTarget());
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentConfirmationService).completeConfirmation(
            PAYMENT_ID,
            PAYMENT_KEY,
            new PaymentGateway.PgApproval(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)
        );
        verify(paymentConfirmationService, never()).failConfirmation(PAYMENT_ID);
    }

    @Test
    void 토스_결제가_실패했으면_결제를_실패_처리한다() {
        when(paymentConfirmationService.getRecoveryTarget(PAYMENT_ID)).thenReturn(recoveryTarget());
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.FAILED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentConfirmationService).failConfirmation(PAYMENT_ID);
        verify(paymentConfirmationService, never()).completeConfirmation(
            PAYMENT_ID,
            PAYMENT_KEY,
            new PaymentGateway.PgApproval(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)
        );
    }

    @Test
    void 토스_결제가_만료되었으면_결제를_실패_처리한다() {
        when(paymentConfirmationService.getRecoveryTarget(PAYMENT_ID)).thenReturn(recoveryTarget());
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.EXPIRED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentConfirmationService).failConfirmation(PAYMENT_ID);
    }

    @Test
    void 토스_결제가_처리중이면_CONFIRMING_상태를_유지한다() {
        when(paymentConfirmationService.getRecoveryTarget(PAYMENT_ID)).thenReturn(recoveryTarget());
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.PENDING));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentConfirmationService, never()).completeConfirmation(
            PAYMENT_ID,
            PAYMENT_KEY,
            new PaymentGateway.PgApproval(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)
        );
        verify(paymentConfirmationService, never()).failConfirmation(PAYMENT_ID);
    }

    private PaymentRecoveryTarget recoveryTarget() {
        return new PaymentRecoveryTarget(PAYMENT_ID, PAYMENT_KEY, PG_ORDER_ID, AMOUNT);
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, status);
    }
}
