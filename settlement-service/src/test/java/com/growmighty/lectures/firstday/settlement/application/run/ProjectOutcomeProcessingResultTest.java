// TODO(settlement-plan): Replace legacy per-outcome statuses with monthly reconciliation and review result assertions.
package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING;
import static com.growmighty.lectures.firstday.settlement.application.run.ProjectOutcomeProcessingStatus.SETTLEMENT_CONFIRMED;
import static com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus.FAILED;
import static com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectOutcomeProcessingResultTest {

    @Test
    @DisplayName("실패 프로젝트를 정산 확정 결과로 만들 수 없다")
    void rejectsConfirmedSettlementForFailedProject() {
        assertThatThrownBy(() -> new ProjectOutcomeProcessingResult(
                101L,
                FAILED,
                SETTLEMENT_CONFIRMED
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 결과와 처리 상태가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("성공 프로젝트를 환불 요청 대기로 만들 수 없다")
    void rejectsRefundPendingForSucceededProject() {
        assertThatThrownBy(() -> new ProjectOutcomeProcessingResult(
                101L,
                SUCCEEDED,
                REFUND_REQUEST_PENDING
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 결과와 처리 상태가 일치하지 않습니다.");
    }
}
