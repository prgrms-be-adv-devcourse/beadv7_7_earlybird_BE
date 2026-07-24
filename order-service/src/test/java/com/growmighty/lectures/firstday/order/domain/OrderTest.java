package com.growmighty.lectures.firstday.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private OrderItem item(long projectId, String price, int quantity) {
        return OrderItem.create("Reward " + projectId, new BigDecimal(price), projectId, projectId, quantity);
    }

    private Order order(List<OrderItem> items) {
        return Order.create(UUID.randomUUID(), 1L, items, "Receiver", "010-0000-0000", "Seoul", "06236");
    }

    @Test
    @DisplayName("주문 생성 시 항목 합계와 총액을 스스로 계산한다")
    void create_calculatesAmounts() {
        Order order = order(List.of(item(1L, "10000", 2)));

        assertThat(order.getItemsAmount().getValue()).isEqualByComparingTo("20000");
        assertThat(order.getShippingFee().getValue()).isEqualByComparingTo("3000");
        assertThat(order.getTotalAmount().getValue()).isEqualByComparingTo("23000");
    }

    @Test
    @DisplayName("합계가 무료배송 기준 이상이면 배송비가 0원이다")
    void shippingFee_free_atOrAboveThreshold() {
        Order order = order(List.of(item(1L, "50000", 1)));

        assertThat(order.getShippingFee().getValue()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount().getValue()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("주문 항목이 없으면 생성할 수 없다")
    void create_withoutItems_throws() {
        assertThatThrownBy(() -> order(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("배송지 정보가 비어 있으면 생성할 수 없다")
    void create_withoutShippingInfo_throws() {
        List<OrderItem> items = List.of(item(1L, "10000", 1));
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), 1L, items, "", "010-0000-0000", "Seoul", "06236"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), 1L, items, "Receiver", null, "Seoul", "06236"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문을 취소하면 상태가 CANCELLED로 전이된다")
    void completePayment_whenNotCreated_throws() {
        Order order = order(List.of(item(1L, "10000", 1)));

        assertThatThrownBy(order::cancel).isInstanceOf(InvalidOrderStatusException.class);

        order.markPaymentRequested();
        order.markPaid(99L);
        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 취소된 주문을 다시 취소하면 예외가 발생한다")
    void cancel_twice_throws() {
        Order order = order(List.of(item(1L, "10000", 1)));

        assertThatThrownBy(() -> order.markPaid(99L)).isInstanceOf(InvalidOrderStatusException.class);
        assertThatThrownBy(order::markPaymentProcessing).isInstanceOf(InvalidOrderStatusException.class);

        order.markPaymentRequested();
        order.markPaid(99L);
        assertThatThrownBy(() -> order.markPaid(100L)).isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    @DisplayName("재계산 총액은 저장된 총액과 항상 일치한다")
    void recalculatedTotal_matchesStored() {
        Order order = order(List.of(item(1L, "10000", 2), item(2L, "5000", 1)));

        assertThat(order.recalculatedTotal().isSameAmount(order.getTotalAmount())).isTrue();
    }
}
