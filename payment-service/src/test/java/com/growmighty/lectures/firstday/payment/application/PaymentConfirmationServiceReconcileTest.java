package com.growmighty.lectures.firstday.payment.application;

import com.growmighty.lectures.firstday.payment.config.PaymentRecoveryProperties;
import com.growmighty.lectures.firstday.payment.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private PaymentRecoveryProperties paymentRecoveryProperties;

    @Mock
    private PaymentStatusOutboxRepository paymentStatusOutboxRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PaymentConfirmationService paymentConfirmationService;

    @Test
    void 완료된_PG_결제는_PAID로_정합화한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentStatusOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0)); // <-- JPA 저장 결과 모사

        paymentConfirmationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.COMPLETED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxRepository).save(any());
        verify(applicationEventPublisher).publishEvent(any(PaymentStatusOutbox.class));
    }

    @Test
    void 이미_PAID인_결제의_완료_웹훅은_무시한다() {
        Payment payment = confirmingPayment();
        payment.confirm(PAYMENT_KEY);
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));

        paymentConfirmationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.COMPLETED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 기존_PAID_Outbox가_있으면_정합화_과정에서_새로_저장하지_않는다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentStatusOutboxRepository.existsByPaymentIdAndPaymentStatus(
            any(),
            eq(PaymentStatus.PAID)
        )).thenReturn(true);

        paymentConfirmationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.COMPLETED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentStatusOutboxRepository).existsByPaymentIdAndPaymentStatus(any(), eq(PaymentStatus.PAID));
        verify(paymentStatusOutboxRepository, never()).save(any());
        verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void 취소된_PG_결제는_FAILED로_정합화한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentConfirmationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.CANCELLED));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxRepository).save(argThat(outbox ->
            outbox.getPaymentStatus() == PaymentStatus.FAILED
        ));
    }

    @Test
    void 확정_실패한_승인은_FAILED로_기록한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentConfirmationService.failConfirmation(payment.getPaymentId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxRepository).save(argThat(outbox ->
            outbox.getPaymentStatus() == PaymentStatus.FAILED
        ));
    }

    // 추가 : 최대 확인 대기 시간 초과 결제의 FAILED 상태를 Order 통지용 Outbox에 저장
    @Test
    void 최대_확인_대기_시간을_초과한_PENDING_결제는_FAILED_Outbox를_저장한다() {
        Payment payment = confirmingPayment();
        when(paymentRepository.findByPgOrderId(payment.getPgOrderId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentRecoveryProperties.maximumConfirmingDuration()).thenReturn(Duration.ZERO);

        paymentConfirmationService.reconcile(pgPayment(payment, PaymentGateway.PgPaymentStatus.PENDING));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentStatusOutboxRepository).save(argThat(outbox ->
            outbox.getPaymentStatus() == PaymentStatus.FAILED
        ));
    }

    // 추가 : 복구 배치가 먼저 완료한 결제의 실패 처리는 무시
    @Test
    void 이미_PAID인_결제의_확정_실패는_무시한다() {
        Payment payment = confirmingPayment();
        payment.confirm(PAYMENT_KEY);
        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));

        paymentConfirmationService.failConfirmation(payment.getPaymentId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void 장기체류한_READY_결제는_FAILED로_변경하고_Outbox를_저장한다() {
        Payment payment = Payment.ready(ORDER_ID, AMOUNT);
        ReflectionTestUtils.setField(payment, "paymentId", 1L);
        ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.now().minusMinutes(31));
        when(paymentRepository.findById(payment.getPaymentId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);
        when(paymentStatusOutboxRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRecoveryProperties.readyTimeOut()).thenReturn(Duration.ofMinutes(30));

        paymentConfirmationService.expireReadyPayment(payment.getPaymentId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
        verify(paymentStatusOutboxRepository).save(argThat(outbox ->
            outbox.getPaymentStatus() == PaymentStatus.FAILED
        ));
        verify(applicationEventPublisher).publishEvent(any(PaymentStatusOutbox.class));
    }

    private Payment confirmingPayment() {
        Payment payment = Payment.ready(ORDER_ID, AMOUNT);
        payment.startConfirming(PAYMENT_KEY);
        return payment;
    }

    private PaymentGateway.PgPayment pgPayment(Payment payment, PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, payment.getPgOrderId(), AMOUNT, status);
    }
}
