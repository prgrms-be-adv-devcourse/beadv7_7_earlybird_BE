package com.growmighty.lectures.firstday.payment.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    private static final String PAYMENT_KEY = "payment-key";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    @InjectMocks
    private PaymentReconciliationService paymentReconciliationService;

    @Test
    void PG_결제_상태_정합화를_위임한다() {
        PaymentGateway.PgPayment pgPayment = pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED);

        paymentReconciliationService.reconcile(pgPayment);

        verify(paymentConfirmationService).reconcile(pgPayment);
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, "pg-order-id", AMOUNT, status);
    }
}
