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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRecoveryServiceTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String PAYMENT_KEY = "payment-key";
    private static final String PG_ORDER_ID = "order-1";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusOutboxAppender paymentStatusOutboxAppender;

    @Mock
    private PaymentRecoveryProperties paymentRecoveryProperties;

    @InjectMocks
    private PaymentRecoveryService paymentRecoveryService;

    @Test
    void 토스_결제가_완료되었으면_결제를_완료_처리한다() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(confirmingPayment())); // <-- 복구 서비스가 직접 조회
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentReconciliationService).reconcile(pgPayment(PaymentGateway.PgPaymentStatus.COMPLETED));
    }

    @Test
    void 토스_결제가_실패했으면_결제를_실패_처리한다() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(confirmingPayment())); // <-- 복구 서비스가 직접 조회
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.FAILED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentReconciliationService).reconcile(pgPayment(PaymentGateway.PgPaymentStatus.FAILED));
    }

    @Test
    void 토스_결제가_만료되었으면_결제를_실패_처리한다() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(confirmingPayment())); // <-- 복구 서비스가 직접 조회
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.EXPIRED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentReconciliationService).reconcile(pgPayment(PaymentGateway.PgPaymentStatus.EXPIRED));
    }

    @Test
    void 토스_결제가_처리중이면_정합화_처리를_위임한다() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(confirmingPayment())); // <-- 복구 서비스가 직접 조회
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.PENDING));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentReconciliationService).reconcile(pgPayment(PaymentGateway.PgPaymentStatus.PENDING));
    }

    @Test
    void 토스_결제가_취소되었으면_결제를_실패_처리한다() {
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(confirmingPayment())); // <-- 복구 서비스가 직접 조회
        when(paymentGateway.getPayment(PAYMENT_KEY))
            .thenReturn(pgPayment(PaymentGateway.PgPaymentStatus.CANCELLED));

        paymentRecoveryService.recover(PAYMENT_ID);

        verify(paymentReconciliationService).reconcile(pgPayment(PaymentGateway.PgPaymentStatus.CANCELLED));
    }

    // 추가 : 장기 체류 READY 결제를 복구 정책에 따라 FAILED로 전이하고 Outbox를 남긴다.
    @Test
    void 장기체류한_READY_결제는_FAILED로_변경하고_Outbox를_저장한다() {
        Payment payment = Payment.ready(1L, 2L, AMOUNT);
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        ReflectionTestUtils.setField(
            payment, "createdAt", LocalDateTime.now().minusMinutes(31)
        );
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRecoveryProperties.readyTimeOut()).thenReturn(Duration.ofMinutes(30));

        paymentRecoveryService.expireReadyPayment(PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentStatusOutboxAppender).savePaymentAndAppendOutbox(payment);
    }

    // 추가 : PG 조회 대상인 CONFIRMING 결제를 만든다.
    private Payment confirmingPayment() {
        Payment payment = Payment.ready(1L, 2L, AMOUNT);
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.startConfirming(PAYMENT_KEY);
        return payment;
    }

    private PaymentGateway.PgPayment pgPayment(PaymentGateway.PgPaymentStatus status) {
        return new PaymentGateway.PgPayment(PAYMENT_KEY, PG_ORDER_ID, AMOUNT, status);
    }
}
