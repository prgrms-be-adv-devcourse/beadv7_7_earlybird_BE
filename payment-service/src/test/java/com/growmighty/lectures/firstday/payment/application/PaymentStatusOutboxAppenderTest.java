package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.domain.Payment;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutbox;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatusOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentStatusOutboxAppenderTest {

    private static final Long PAYMENT_ID = 1L;
    private static final Long ORDER_ID = 2L;

    @Mock
    private PaymentStatusOutboxRepository paymentStatusOutboxRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PaymentStatusOutboxAppender paymentStatusOutboxAppender;

    // 추가 : 신규 결제 상태 Outbox를 저장하고 커밋 후 즉시 발행 이벤트를 전달한다.
    @Test
    void 존재하지_않는_결제_상태_Outbox를_저장하고_발행한다() {
        Payment payment = paidPayment();
        when(paymentStatusOutboxRepository.existsByPaymentIdAndPaymentStatus(PAYMENT_ID, PaymentStatus.PAID))
            .thenReturn(false);
        when(paymentStatusOutboxRepository.save(any(PaymentStatusOutbox.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        paymentStatusOutboxAppender.appendIfAbsent(payment);

        ArgumentCaptor<PaymentStatusOutbox> captor = ArgumentCaptor.forClass(PaymentStatusOutbox.class);
        verify(paymentStatusOutboxRepository).save(captor.capture());
        PaymentStatusOutbox outbox = captor.getValue();
        assertThat(outbox.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(outbox.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(outbox.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(applicationEventPublisher).publishEvent(outbox);
    }

    // 추가 : 동일 결제 상태 Outbox가 있으면 중복 저장과 발행을 생략한다.
    @Test
    void 이미_존재하는_결제_상태_Outbox는_저장하거나_발행하지_않는다() {
        Payment payment = paidPayment();
        when(paymentStatusOutboxRepository.existsByPaymentIdAndPaymentStatus(PAYMENT_ID, PaymentStatus.PAID))
            .thenReturn(true);

        paymentStatusOutboxAppender.appendIfAbsent(payment);

        verify(paymentStatusOutboxRepository, never()).save(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    private Payment paidPayment() {
        Payment payment = Payment.ready(1L, ORDER_ID, BigDecimal.valueOf(10_000));
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming("payment-key");
        payment.confirm("payment-key");
        return payment;
    }
}
