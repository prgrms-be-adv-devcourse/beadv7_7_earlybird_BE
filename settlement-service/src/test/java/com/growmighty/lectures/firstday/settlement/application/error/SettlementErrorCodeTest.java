// TODO(settlement-plan): Cover reconciliation, event-contract, and REVIEW_REQUIRED errors and delete legacy HTTP cancellation cases.
package com.growmighty.lectures.firstday.settlement.application.error;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettlementErrorCodeTest {

    @Test
    @DisplayName("Order 정산 입력 오류는 기존 S002 계약을 유지한다")
    void preservesOrderPaymentInputErrorContract() {
        assertThat(ORDER_PAYMENT_INPUTS_UNAVAILABLE.getCode()).isEqualTo("S002");
        assertThat(ORDER_PAYMENT_INPUTS_UNAVAILABLE.getMessage())
                .isEqualTo("주문 결제금액을 확인할 수 없습니다.");
    }
}
