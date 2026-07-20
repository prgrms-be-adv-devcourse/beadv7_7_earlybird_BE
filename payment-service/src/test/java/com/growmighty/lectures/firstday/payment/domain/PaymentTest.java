package com.growmighty.lectures.firstday.payment.domain;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
class PaymentTest {

    @Test
    @DisplayName("0 이하 금액으로는 결제를 생성할 수 없다")
    void ready_invalidAmount_throws() {
        assertThatThrownBy(() -> Payment.ready(1L, 1L, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutOrderId_throws() {
        assertThatThrownBy(() -> Payment.ready(null, 1L, BigDecimal.valueOf(10000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("사용자 식별자 없이는 결제를 생성할 수 없다")
    void ready_withoutUserId_throws() {
        assertThatThrownBy(() -> Payment.ready(1L, null, BigDecimal.valueOf(10000)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인하면 PAID로 전이되고 거래번호가 저장된다")
    void confirm_transitions() {
        Payment payment = Payment.ready(1L, 1L, BigDecimal.valueOf(10000));

        payment.confirm("PG-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPgTransactionId()).isEqualTo("PG-1");
        assertThat(payment.isPaid()).isTrue();
    }

    @Test
    @DisplayName("이미 승인된 결제를 다시 승인하면 예외가 발생한다")
    void confirm_twice_throws() {
        Payment payment = Payment.ready(1L, 1L, BigDecimal.valueOf(10000));
        payment.confirm("PG-1");

        assertThatThrownBy(() -> payment.confirm("PG-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제 완료 상태에서만 취소할 수 있다")
    void cancel_onlyFromPaid() {
        Payment paid = Payment.ready(1L, 1L, BigDecimal.valueOf(10000));
        paid.confirm("PG-1");
        paid.cancel();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        Payment ready = Payment.ready(1L, 1L, BigDecimal.valueOf(10000));
        assertThatThrownBy(ready::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("승인 대기 상태에서 실패 처리하면 FAILED로 전이된다")
    void fail_transitions() {
        Payment payment = Payment.ready(1L, 1L, BigDecimal.valueOf(10000));

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
