package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentRepository;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceReconcileTest {

    private static final Long ORDER_ID = 1L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    private static final String PAYMENT_KEY = "payment-key";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusOutboxAppender paymentStatusOutboxAppender;

    @InjectMocks
    private PaymentConfirmationService paymentConfirmationService;

    @Test
    void 확정_실패한_승인은_FAILED로_기록한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));

        paymentConfirmationService.failConfirmation(payment.getPaymentId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentStatusOutboxAppender).savePaymentAndAppendOutbox(payment);
    }

    @Test
    void 이미_PAID인_결제의_확정_실패는_무시한다() {
        Payment payment = confirmingPayment();
        payment.confirm(PAYMENT_KEY);
        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));

        paymentConfirmationService.failConfirmation(payment.getPaymentId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(paymentStatusOutboxAppender);
    }

    private Payment confirmingPayment() {
        Payment payment = Payment.ready(1L, ORDER_ID, AMOUNT);
        ReflectionTestUtils.setField(payment, "paymentId", 1L);
        payment.startConfirming(PAYMENT_KEY);
        return payment;
    }
}
