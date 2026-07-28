package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.application.port.OrderStatusPort;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 2L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    @Mock
    private OrderStatusPort orderStatusPort;

    @InjectMocks
    private PaymentReconciliationService paymentReconciliationService;

    @Test
    void 결제가_완료로_정합화되면_주문_상태를_통보한다() {
        PaymentGateway.PgPayment pgPayment = pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED);
        PaymentInfo paymentInfo = new PaymentInfo(PAYMENT_ID, ORDER_ID, AMOUNT, PaymentStatus.PAID);
        when(paymentConfirmationService.reconcile(pgPayment)).thenReturn(Optional.of(paymentInfo));

        paymentReconciliationService.reconcile(pgPayment);

        verify(orderStatusPort).notifyStatus(ORDER_ID, PaymentStatus.PAID);
    }

    @Test
    void 결제_상태가_변경되지_않으면_주문_상태를_통보하지_않는다() {
        PaymentGateway.PgPayment pgPayment = pgPayment(PaymentGateway.PgPaymentStatus.PENDING);
        when(paymentConfirmationService.reconcile(pgPayment)).thenReturn(Optional.empty());

        paymentReconciliationService.reconcile(pgPayment);

        verifyNoInteractions(orderStatusPort);
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, "pg-order-id", AMOUNT, status);
    }
}
