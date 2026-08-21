package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentCancelRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import com.growmighty.lectures.firstday.refund.application.RefundCancellationSagaOrchestrator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentInternalControllerTest {

    private static final Long ORDER_ID = 1L;
    private static final Long PAYMENT_ID = 2L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Test
    void 주문_ID로_내부_결제를_조회한다() {
        PaymentService paymentService = mock(PaymentService.class);
        RefundCancellationSagaOrchestrator refundCancellationSagaOrchestrator = mock(RefundCancellationSagaOrchestrator.class);
        PaymentInternalController controller = new PaymentInternalController(
            paymentService,
            refundCancellationSagaOrchestrator
        );
        PaymentInfo payment = paymentInfo();
        when(paymentService.getPaymentByOrderId(ORDER_ID)).thenReturn(payment);

        PaymentResponse response = controller.getPaymentByOrderId(ORDER_ID);

        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID.getCode());
        verify(paymentService).getPaymentByOrderId(ORDER_ID);
    }

    @Test
    void 주문_ID와_결제_ID로_내부_취소를_요청하고_취소된_결제를_반환한다() {
        PaymentService paymentService = mock(PaymentService.class);
        RefundCancellationSagaOrchestrator refundCancellationSagaOrchestrator = mock(RefundCancellationSagaOrchestrator.class);
        PaymentInternalController controller = new PaymentInternalController(
            paymentService,
            refundCancellationSagaOrchestrator
        );
        PaymentInfo cancelledPayment = new PaymentInfo(
            PAYMENT_ID,
            ORDER_ID,
            "pg-order-id",
            AMOUNT,
            PaymentStatus.CANCELLED
        );
        when(paymentService.getPaymentForInternal(PAYMENT_ID)).thenReturn(cancelledPayment);

        PaymentResponse response = controller.cancel(new PaymentCancelRequest(ORDER_ID, PAYMENT_ID));

        assertThat(response.status()).isEqualTo(PaymentStatus.CANCELLED.getCode());
        verify(refundCancellationSagaOrchestrator).cancel(ORDER_ID, PAYMENT_ID);
        verify(paymentService).getPaymentForInternal(PAYMENT_ID);
    }

    private PaymentInfo paymentInfo() {
        return new PaymentInfo(
            PAYMENT_ID,
            ORDER_ID,
            "pg-order-id",
            AMOUNT,
            PaymentStatus.PAID
        );
    }
}
