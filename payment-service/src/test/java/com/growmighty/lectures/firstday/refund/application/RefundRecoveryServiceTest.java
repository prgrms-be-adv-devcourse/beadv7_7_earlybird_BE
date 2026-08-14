package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.domain.vo.SensitiveValue;
import com.growmighty.lectures.firstday.refund.application.dto.RefundRecoveryTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRecoveryServiceTest {

    private static final Long REFUND_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private RefundService refundService;

    @InjectMocks
    private RefundRecoveryService refundRecoveryService;

    @Test
    void recover_completesRefundWhenPgPaymentIsCancelled() {
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.CANCELLED));

        refundRecoveryService.recover(recoveryTarget()); // <-- 사전 조회된 paymentKey 사용

        verify(refundService).completeRefund(REFUND_ID);
        verify(refundService, never()).failRefund(any());
    }

    @Test
    void recover_failsRefundWhenPgPaymentIsTerminalButNotCancelled() {
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED));

        refundRecoveryService.recover(recoveryTarget()); // <-- 사전 조회된 paymentKey 사용

        verify(refundService).failRefund(REFUND_ID);
        verify(refundService, never()).completeRefund(any());
    }

    @Test
    void recover_schedulesRefundRetryWhenPgPaymentIsPending() {
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.PENDING));

        refundRecoveryService.recover(recoveryTarget()); // <-- 사전 조회된 paymentKey 사용

        verify(refundService).scheduleRetry(REFUND_ID);
        verify(refundService, never()).completeRefund(any());
        verify(refundService, never()).failRefund(any());
    }

    // 추가 : 복구 대상 DTO 생성, PG 조회 입력값
    private RefundRecoveryTarget recoveryTarget() {
        return new RefundRecoveryTarget(REFUND_ID, new SensitiveValue(PAYMENT_KEY));
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, "pg-order-id", BigDecimal.valueOf(10_000), status);
    }
}
