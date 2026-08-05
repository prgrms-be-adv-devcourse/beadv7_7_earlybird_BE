package com.growmighty.lectures.firstday.settlement.application.port.order;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectOrderContractTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("프로젝트 식별자는 양수여야 한다")
    void rejectsInvalidProjectId(Long projectId) {
        assertThatThrownBy(() -> new ProjectOrders(projectId, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("주문 식별자는 양수여야 한다")
    void rejectsInvalidOrderId(Long orderId) {
        assertThatThrownBy(() -> new OrderPayment(orderId, Money.wons(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문 결제금액은 필수이고 음수일 수 없다")
    void rejectsMissingOrNegativePaymentAmount() {
        assertThatThrownBy(() -> new OrderPayment(1L, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrderPayment(1L, Money.wons(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("프로젝트 주문 목록과 각 주문 항목은 필수다")
    void rejectsMissingOrders() {
        assertThatThrownBy(() -> new ProjectOrders(1L, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProjectOrders(1L, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("한 프로젝트 안에서 주문 식별자는 중복될 수 없다")
    void rejectsDuplicateOrderIdWithinProject() {
        assertThatThrownBy(() -> new ProjectOrders(1L, List.of(
                new OrderPayment(10L, Money.wons(1_000)),
                new OrderPayment(10L, Money.wons(2_000))
        )))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
