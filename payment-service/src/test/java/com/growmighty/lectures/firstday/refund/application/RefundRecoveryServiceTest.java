package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRecoveryServiceTest {

    private static final Long REFUND_ID = 1L;
    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private RefundService refundService;

    @InjectMocks
    private RefundRecoveryService refundRecoveryService;

    @Test
    void recover_completesRefundWhenPgPaymentIsCancelled() {
        givenRequestedRefund();
        givenPayment();
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.CANCELLED));

        refundRecoveryService.recover(REFUND_ID);

        verify(refundService).completeRefund(REFUND_ID);
        verify(refundService, never()).failRefund(any());
    }

    @Test
    void recover_failsRefundWhenPgPaymentIsTerminalButNotCancelled() {
        givenRequestedRefund();
        givenPayment();
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED));

        refundRecoveryService.recover(REFUND_ID);

        verify(refundService).failRefund(REFUND_ID);
        verify(refundService, never()).completeRefund(any());
    }

    @Test
    void recover_schedulesRefundRetryWhenPgPaymentIsPending() {
        givenRequestedRefund();
        givenPayment();
        when(paymentGateway.getPayment(PAYMENT_KEY)).thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.PENDING));

        refundRecoveryService.recover(REFUND_ID);

        verify(refundService).scheduleRetry(REFUND_ID);
        verify(refundService, never()).completeRefund(any());
        verify(refundService, never()).failRefund(any());
    }

    @Test
    void recover_skipsAlreadyProcessedRefund() {
        Refund refund = requestedRefund();
        refund.complete();
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        refundRecoveryService.recover(REFUND_ID);

        verifyNoInteractions(paymentRepository, paymentGateway, refundService);
    }

    private void givenRequestedRefund() {
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(requestedRefund()));
    }

    private void givenPayment() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
    }

    private Refund requestedRefund() {
        Refund refund = Refund.request(PAYMENT_ID, BigDecimal.valueOf(10_000), RefundReason.USER_CANCEL);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private Payment paidPayment() {
        Payment payment = Payment.ready(ORDER_ID, BigDecimal.valueOf(10_000));
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming(PAYMENT_KEY);
        payment.confirm(PAYMENT_KEY);
        return payment;
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, "pg-order-id", BigDecimal.valueOf(10_000), status);
    }
}
