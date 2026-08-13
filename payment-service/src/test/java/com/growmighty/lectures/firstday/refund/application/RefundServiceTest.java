package com.growmighty.lectures.firstday.refund.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import com.growmighty.lectures.firstday.refund.application.dto.RefundCancellationTarget;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 1L;
    private static final Long REFUND_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentStatusOutboxRepository paymentStatusOutboxRepository;

    @InjectMocks
    private RefundService refundService;

    @Test
    void startRefund_createsRequestedRefundAndReturnsTarget() {
        Payment payment = paidPayment();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(refundRepository.save(any(Refund.class)))
            .thenAnswer(invocation -> {
                Refund refund = invocation.getArgument(0);
                ReflectionTestUtils.setField(refund, "id", REFUND_ID);
                return refund;
            });

        RefundCancellationTarget target = refundService.startRefund(PAYMENT_ID, RefundReason.USER_CANCEL);

        assertThat(target.refundId()).isEqualTo(REFUND_ID);
        assertThat(target.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(target.reason()).isEqualTo(RefundReason.USER_CANCEL);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);

        verify(refundRepository).save(any(Refund.class));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void startRefund_reusesExistingRequestedRefund() {
        Payment payment = paidPayment();
        Refund refund = requestedRefund();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(refundRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(refund));

        RefundCancellationTarget target = refundService.startRefund(PAYMENT_ID, RefundReason.USER_CANCEL);

        assertThat(target.refundId()).isEqualTo(REFUND_ID);
        verify(refundRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void startPlannedRefund_transitionsPlannedRefundToRequested() {
        Payment payment = paidPayment();
        Refund refund = Refund.planned(PAYMENT_ID, 10L, AMOUNT, RefundReason.GOAL_FAILED);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        RefundCancellationTarget target = refundService.startPlannedRefund(REFUND_ID);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(target.reason()).isEqualTo(RefundReason.GOAL_FAILED);
        verify(refundRepository).save(refund);
    }

    @Test
    void completeRefund_completesRefundAndCancelsPayment() {
        Payment payment = paidPayment();
        Refund refund = requestedRefund();
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        refundService.completeRefund(REFUND_ID);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(refundRepository).save(refund);
        verify(paymentRepository).save(payment);
    }

    @Test
    void failRefund_marksRefundFailedAndKeepsPaymentPaid() {
        Payment payment = paidPayment();
        Refund refund = requestedRefund();
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        refundService.failRefund(REFUND_ID);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(refundRepository).save(refund);
        verifyNoInteractions(paymentRepository);
    }

    // 추가 : 정합화가 먼저 완료한 환불의 실패 처리는 무시
    @Test
    void failRefund_ignoresAlreadyCompletedRefund() {
        Refund refund = requestedRefund();
        refund.complete();
        when(refundRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));

        refundService.failRefund(REFUND_ID);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        verify(refundRepository, never()).save(any());
    }

    @Test
    void startRefund_rejectsNotPaidPayment() {
        Payment payment = Payment.ready(ORDER_ID, AMOUNT);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> refundService.startRefund(PAYMENT_ID, RefundReason.USER_CANCEL))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PAID 상태의 결제만 환불할 수 있습니다.");

        verifyNoInteractions(refundRepository);
    }

    // 추가 : Saga 시작 전 REQUESTED 환불 생성
    private Refund requestedRefund() {
        Refund refund = Refund.request(PAYMENT_ID, AMOUNT, RefundReason.USER_CANCEL);
        ReflectionTestUtils.setField(refund, "id", REFUND_ID);
        return refund;
    }

    private Payment paidPayment() {
        Payment payment = Payment.ready(ORDER_ID, AMOUNT);
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming(PAYMENT_KEY);
        payment.confirm(PAYMENT_KEY);
        return payment;
    }
}
