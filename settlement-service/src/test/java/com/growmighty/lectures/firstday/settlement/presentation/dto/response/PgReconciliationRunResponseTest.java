package com.growmighty.lectures.firstday.settlement.presentation.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.run.PgReconciliationRunResult;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PgReconciliationRunResponseTest {

    @Test
    @DisplayName("PG 대사 실행 상태와 대사 완료 주문 식별자를 내부 실행 응답에 보존한다")
    void preservesRunStatusAndConfirmedOrderIds() {
        PgReconciliationRunResult result = new PgReconciliationRunResult(
                1L,
                YearMonth.of(2026, 7),
                PgReconciliationRun.Status.COMPLETED,
                List.of(1001L, 1002L)
        );

        PgReconciliationRunResponse response = PgReconciliationRunResponse.from(result);

        assertThat(response).isEqualTo(new PgReconciliationRunResponse(
                1L,
                YearMonth.of(2026, 7),
                PgReconciliationRun.Status.COMPLETED,
                List.of(1001L, 1002L)
        ));
    }
}
