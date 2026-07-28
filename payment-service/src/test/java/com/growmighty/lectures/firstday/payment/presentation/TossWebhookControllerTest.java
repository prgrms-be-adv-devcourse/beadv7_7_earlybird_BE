package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.application.PaymentReconciliationService;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossWebhookPayment;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossWebhookRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

class TossWebhookControllerTest {

    private static final String PAYMENT_KEY = "payment-key";

    @Test
    void 웹훅의_paymentKey로_Toss_결제를_조회하고_정합화를_요청한다() {
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        PaymentReconciliationService paymentReconciliationService = mock(PaymentReconciliationService.class);
        TossWebhookController controller = new TossWebhookController(paymentGateway, paymentReconciliationService);
        PaymentGateway.PgPayment pgPayment = new PaymentGateway.PgPayment(
            PAYMENT_KEY,
            "pg-order-id",
            BigDecimal.valueOf(10_000),
            PaymentGateway.PgPaymentStatus.COMPLETED
        );
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment);

        controller.receive(new TossWebhookRequest("PAYMENT_STATUS_CHANGED", new TossWebhookPayment(PAYMENT_KEY)));

        verify(paymentGateway).getPayment(PAYMENT_KEY);
        verify(paymentReconciliationService).reconcile(pgPayment);
    }
}
