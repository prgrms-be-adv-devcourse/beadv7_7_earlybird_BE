package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentRecoveryTarget;
import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String PG_ORDER_ID = "order-1";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    private static final Duration MAXIMUM_CONFIRMING_DURATION = Duration.ofMinutes(10);

    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentRecoveryProperties paymentRecoveryProperties;

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
    void 토스_결제가_처리중이면_CONFIRMING_만료_여부를_확인한다() {
        when(paymentRecoveryProperties.maximumConfirmingDuration())
            .thenReturn(MAXIMUM_CONFIRMING_DURATION);
        when(paymentConfirmationService.getRecoveryTarget(PAYMENT_ID)).thenReturn(recoveryTarget());
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.PENDING));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentConfirmationService).failConfirmationIfExpired(
            eq(PAYMENT_ID),
            any(LocalDateTime.class),
            eq(MAXIMUM_CONFIRMING_DURATION)
        );
        verify(paymentConfirmationService, never()).completeConfirmation(
            PAYMENT_ID,
            PAYMENT_KEY,
            new PaymentGateway.PgApproval(PAYMENT_KEY, PG_ORDER_ID, AMOUNT)
        );
        verify(paymentConfirmationService, never()).failConfirmation(PAYMENT_ID);
    }

    @Test
    void 토스_결제가_취소되었으면_결제를_실패_처리한다() {
        when(paymentConfirmationService.getRecoveryTarget(PAYMENT_ID)).thenReturn(recoveryTarget());
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.CANCELLED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentConfirmationService).failConfirmation(PAYMENT_ID);
    }

    private PaymentRecoveryTarget recoveryTarget() {
        return new PaymentRecoveryTarget(PAYMENT_ID, PAYMENT_KEY);
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, status);
    }
}
