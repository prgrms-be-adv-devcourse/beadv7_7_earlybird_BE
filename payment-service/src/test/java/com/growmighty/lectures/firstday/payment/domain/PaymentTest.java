package com.growmighty.lectures.firstday.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final String PG_ORDER_ID = "order_123";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10_000);

    @Test
    @DisplayName("0 이하 금액으로는 결제를 생성할 수 없다")
    void ready_invalidAmount_throws() {
        assertThatThrownBy(() -> Payment.ready(
            ORDER_ID, PG_ORDER_ID, USER_ID, BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutOrderId_throws() {
        assertThatThrownBy(() -> Payment.ready(
            null, PG_ORDER_ID, USER_ID, AMOUNT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("사용자 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutUserId_throws() {
        assertThatThrownBy(() -> Payment.ready(
            ORDER_ID, PG_ORDER_ID, null, AMOUNT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("READY 결제를 만들면 승인 재시도용 멱등키가 생성된다")
    void ready_generatesApproveIdempotencyKey() {
        Payment payment = Payment.ready(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);

        assertThat(payment.getApproveIdempotencyKey()).isNotBlank();
    }

    @Test
    @DisplayName("READY 결제는 승인 처리를 시작하면 CONFIRMING으로 전이된다")
    void startConfirming_transitions() {
        Payment payment = readyPayment();

        payment.startConfirming();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        assertThat(payment.isConfirming()).isTrue();
    }

    @Test
    @DisplayName("CONFIRMING 상태에서 승인하면 PAID로 전이되고 paymentKey가 저장된다")
    void confirm_transitions() {
        Payment payment = readyPayment();
        payment.startConfirming();

        payment.confirm("payment-key-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key-1");
        assertThat(payment.isPaid()).isTrue();
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
        payment.startConfirming();
        payment.confirm("payment-key-1");

        assertThatThrownBy(() -> payment.confirm("payment-key-1"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CONFIRMING 상태에서 실패 처리하면 FAILED로 전이된다")
    void fail_transitions() {
        Payment payment = readyPayment();
        payment.startConfirming();

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private Payment readyPayment() {
        return Payment.ready(ORDER_ID, PG_ORDER_ID, USER_ID, AMOUNT);
    }
}
