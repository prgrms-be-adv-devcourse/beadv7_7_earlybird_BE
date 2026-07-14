package com.growmighty.lectures.firstday.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    @DisplayName("0 이하 금액으로는 결제를 생성할 수 없다")
    void ready_invalidAmount_throws() {
        assertThatThrownBy(() -> Payment.ready(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("승인하면 PAID로 전이되고 거래번호가 저장된다")
    void approve_transitions() {
        Payment payment = Payment.ready(BigDecimal.valueOf(10000));

        payment.approve("PG-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPgTransactionId()).isEqualTo("PG-1");
        assertThat(payment.isPaid()).isTrue();
    }

    @Test
    @DisplayName("이미 승인된 결제를 다시 승인하면 예외가 발생한다")
    void approve_twice_throws() {
        Payment payment = Payment.ready(BigDecimal.valueOf(10000));
        payment.approve("PG-1");

        assertThatThrownBy(() -> payment.approve("PG-2"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제 완료 상태에서만 취소할 수 있다")
    void cancel_onlyFromPaid() {
        Payment paid = Payment.ready(BigDecimal.valueOf(10000));
        paid.approve("PG-1");
        paid.cancel();
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        Payment ready = Payment.ready(BigDecimal.valueOf(10000));
        assertThatThrownBy(ready::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("승인 대기 상태에서 실패 처리하면 FAILED로 전이된다")
    void fail_transitions() {
        Payment payment = Payment.ready(BigDecimal.valueOf(10000));

        payment.fail();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
