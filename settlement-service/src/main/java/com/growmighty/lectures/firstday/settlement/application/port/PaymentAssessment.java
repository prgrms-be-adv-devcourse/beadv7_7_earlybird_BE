package com.growmighty.lectures.firstday.settlement.application.port;

import com.growmighty.lectures.firstday.settlement.domain.Money;
import java.util.Objects;

public sealed interface PaymentAssessment {

    Long orderId();

    static PaymentAssessment ready(Long orderId, Money finalEffectiveAmount) {
        return new Ready(orderId, finalEffectiveAmount);
    }

    static PaymentAssessment notReady(Long orderId) {
        return new NotReady(orderId);
    }

    static PaymentAssessment noPayment(Long orderId) {
        return new NoPayment(orderId);
    }

    record Ready(Long orderId, Money finalEffectiveAmount) implements PaymentAssessment {

        public Ready {
            requireOrderId(orderId);
            Objects.requireNonNull(finalEffectiveAmount, "최종 유효 결제 금액은 필수입니다.");
        }
    }

    record NotReady(Long orderId) implements PaymentAssessment {

        public NotReady {
            requireOrderId(orderId);
        }
    }

    record NoPayment(Long orderId) implements PaymentAssessment {

        public NoPayment {
            requireOrderId(orderId);
        }
    }

    private static void requireOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("주문 식별자는 양수여야 합니다.");
        }
    }
}
