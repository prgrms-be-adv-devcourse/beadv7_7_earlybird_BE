package com.growmighty.lectures.firstday.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private OrderItem item(long productId, String price, int quantity) {
        return OrderItem.create("상품-" + productId, new BigDecimal(price), productId, quantity);
    }

    @Test
    @DisplayName("주문 생성 시 항목 합계와 총액을 스스로 계산한다")
    void create_calculatesAmounts() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 2)));

        assertThat(order.getItemsAmount().getValue()).isEqualByComparingTo("20000");
        assertThat(order.getTotalAmount().getValue()).isEqualByComparingTo("23000");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("상품 합계가 무료배송 기준(50000) 미만이면 배송비 3000원이 붙는다")
    void shippingFee_charged_belowThreshold() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 1)));

        assertThat(order.getShippingFee().getValue()).isEqualByComparingTo("3000");
        assertThat(order.getTotalAmount().getValue()).isEqualByComparingTo("13000");
    }

    @Test
    @DisplayName("상품 합계가 무료배송 기준 이상이면 배송비가 0원이다")
    void shippingFee_free_atOrAboveThreshold() {
        Order order = Order.create(1L, List.of(item(1L, "50000", 1)));

        assertThat(order.getShippingFee().getValue()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount().getValue()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("주문 항목이 없으면 생성할 수 없다")
    void create_withoutItems_throws() {
        assertThatThrownBy(() -> Order.create(1L, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제 완료 시 상태가 PAID로 전이되고 결제 ID가 연결된다")
    void completePayment_transitions() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 1)));

        order.completePayment(99L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("CREATED가 아닌 상태에서 결제 완료를 호출하면 예외가 발생한다")
    void completePayment_whenNotCreated_throws() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 1)));
        order.completePayment(99L);

        assertThatThrownBy(() -> order.completePayment(100L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문을 취소하면 상태가 CANCELLED로 전이된다")
    void cancel_transitions() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 1)));

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 취소된 주문을 다시 취소하면 예외가 발생한다")
    void cancel_twice_throws() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 1)));
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("재계산 총액은 저장된 총액과 항상 일치한다")
    void recalculatedTotal_matchesStored() {
        Order order = Order.create(1L, List.of(item(1L, "10000", 2), item(2L, "5000", 1)));

        assertThat(order.recalculatedTotal().isSameAmount(order.getTotalAmount())).isTrue();
    }
}
