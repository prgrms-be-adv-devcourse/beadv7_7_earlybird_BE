package com.growmighty.lectures.firstday.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);
    private static final String PAYMENT_KEY = "payment-key-1";

    @Test
    @DisplayName("0 이하 금액으로는 결제를 생성할 수 없다")
    void ready_invalidAmount_throws() {
        assertThatThrownBy(() -> Payment.ready(USER_ID, ORDER_ID, BigDecimal.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutOrderId_throws() {
        assertThatThrownBy(() -> Payment.ready(USER_ID, null, AMOUNT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("READY 결제를 만들면 PG 주문번호와 승인 재시도용 멱등키가 생성된다")
    void ready_generatesPgOrderIdAndApproveIdempotencyKey() {
        Payment payment = Payment.ready(USER_ID, ORDER_ID, AMOUNT);

        assertThat(payment.getPgOrderId()).startsWith("order-" + ORDER_ID + "-");
        assertThat(payment.getPgOrderId()).hasSizeLessThanOrEqualTo(64);
        assertThat(payment.getApproveIdempotencyKey().value()).isNotBlank();
    }

    @Test
    @DisplayName("READY 결제는 승인 처리를 시작하면 CONFIRMING으로 전이된다")
    void startConfirming_transitions() {
        Payment payment = readyPayment();

        payment.startConfirming(PAYMENT_KEY);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(payment.isConfirming()).isTrue();
        assertThat(payment.getConfirmingAt()).isNotNull();
        assertThat(payment.getPaymentKey().value()).isEqualTo(PAYMENT_KEY);
    }

    @Test
    @DisplayName("CONFIRMING 상태에서 승인하면 PAID로 전이되고 paymentKey가 저장된다")
    void confirm_transitions() {
        Payment payment = readyPayment();
        payment.startConfirming(PAYMENT_KEY);

        payment.confirm(PAYMENT_KEY);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey().value()).isEqualTo(PAYMENT_KEY);
        assertThat(payment.isPaid()).isTrue();
        assertThat(payment.getConfirmingAt()).isNull();
    }

    @Test
    @DisplayName("READY 상태에서는 바로 승인할 수 없다")
    void confirm_fromReady_throws() {
        Payment payment = readyPayment();

        assertThatThrownBy(() -> payment.confirm("payment-key-1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 승인된 결제를 다시 승인하면 예외가 발생한다")
    void confirm_twice_throws() {
        Payment payment = readyPayment();
        payment.startConfirming(PAYMENT_KEY);
        payment.confirm(PAYMENT_KEY);

        assertThatThrownBy(() -> payment.confirm(PAYMENT_KEY))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CONFIRMING 상태에서 실패 처리하면 FAILED로 전이된다")
    void fail_transitions() {
        Payment payment = readyPayment();
        payment.startConfirming(PAYMENT_KEY);

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getConfirmingAt()).isNull();
    }

    @Test
    @DisplayName("CONFIRMING 결제는 최대 대기 시간 전에는 실패 처리되지 않는다")
    void failIfConfirmingExpired_beforeMaximumDuration_returnsFalse() {
        Payment payment = readyPayment();
        payment.startConfirming(PAYMENT_KEY);
        Duration maximumDuration = Duration.ofMinutes(10);
        LocalDateTime beforeExpiration = payment.getConfirmingAt()
            .plus(maximumDuration)
            .minusNanos(1);

        assertThat(payment.failIfConfirmingExpired(beforeExpiration, maximumDuration)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(payment.getConfirmingAt()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMING 결제는 최대 대기 시간에 도달하면 실패 처리된다")
    void failIfConfirmingExpired_atMaximumDuration_transitionsToFailed() {
        Payment payment = readyPayment();
        payment.startConfirming(PAYMENT_KEY);
        Duration maximumDuration = Duration.ofMinutes(10);
        LocalDateTime expiration = payment.getConfirmingAt().plus(maximumDuration);

        assertThat(payment.failIfConfirmingExpired(expiration, maximumDuration)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getConfirmingAt()).isNull();
    }

    @Test
    @DisplayName("READY 결제는 만료 시간 전에는 실패 처리되지 않는다")
    void failIfReadyExpired_beforeMaximumDuration_returnsFalse() {
        Payment payment = readyPayment();
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        Duration maximumDuration = Duration.ofMinutes(30);

        assertThat(payment.failIfReadyExpired(createdAt.plus(maximumDuration).minusNanos(1), maximumDuration))
            .isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    @DisplayName("READY 결제는 만료 시간에 도달하면 FAILED로 전이된다")
    void failIfReadyExpired_atMaximumDuration_transitionsToFailed() {
        Payment payment = readyPayment();
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(30);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        Duration maximumDuration = Duration.ofMinutes(30);

        assertThat(payment.failIfReadyExpired(createdAt.plus(maximumDuration), maximumDuration))
            .isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private Payment readyPayment() {
        return Payment.ready(USER_ID, ORDER_ID, AMOUNT);
    }
}
