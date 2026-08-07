// TODO(settlement-plan): Add pgOrderId and payment status so Kafka facts and Feign recovery share one validated payment shape.
package com.growmighty.lectures.firstday.settlement.application.port.order;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import java.util.Objects;

public record OrderPayment(
        Long orderId,
        Money paymentAmount
) {

    public OrderPayment {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
        Objects.requireNonNull(paymentAmount, "주문 결제 금액은 필수입니다.");
    }
}
