package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRecoveryProperties paymentRecoveryProperties;

    @Mock
    private PaymentStatusOutboxAppender paymentStatusOutboxAppender;

    @InjectMocks
    private PaymentReconciliationService paymentReconciliationService;

    @Test
    void 완료된_PG_결제는_PAID로_정합화한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));

        paymentReconciliationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.COMPLETED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxAppender).appendIfAbsent(payment);
    }

    @Test
    void 이미_PAID인_결제의_완료_웹훅은_무시한다() {
        Payment payment = confirmingPayment();
        payment.confirm(PAYMENT_KEY);
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));

        paymentReconciliationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.COMPLETED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentStatusOutboxAppender);
    }

    @Test
    void 취소된_PG_결제는_FAILED로_정합화한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));

        paymentReconciliationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.CANCELLED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxAppender).appendIfAbsent(payment);
    }

    @Test
    void 최대_확인_대기_시간을_초과한_PENDING_결제는_FAILED로_정합화한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));
        when(paymentRecoveryProperties.maximumConfirmingDuration()).thenReturn(Duration.ZERO);

        paymentReconciliationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.PENDING));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxAppender).appendIfAbsent(payment);
    }

    private Payment confirmingPayment() {
        Payment payment = Payment.ready(1L, ORDER_ID, AMOUNT);
        payment.startConfirming(PAYMENT_KEY);
        return payment;
    }

    private PaymentGateway.PgPayment pgPayment(Payment payment, PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, payment.getPgOrderId(), AMOUNT, status);
    }
}
