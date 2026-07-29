package com.growmighty.lectures.firstday.settlement.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectSettlementTargetContractTest {

    @Test
    @DisplayName("정산 월로 프로젝트 식별자·제목·창작자 식별자를 조회한다")
    void readsSettlementTargetsForMonth() {
        AtomicReference<YearMonth> requestedMonth = new AtomicReference<>();
        ProjectSettlementTargetReader reader = settlementMonth -> {
            requestedMonth.set(settlementMonth);
            return List.of(new ProjectSettlementTarget(101L, 201L));
        };

        List<ProjectSettlementTarget> targets = reader.findSettlementTargets(YearMonth.of(2026, 7));

        assertThat(requestedMonth.get()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(targets).containsExactly(
                new ProjectSettlementTarget(101L, 201L)
        );
    }

    @Test
    @DisplayName("유효하지 않은 프로젝트 식별자를 거부한다")
    void rejectsInvalidProjectId() {
        assertThatThrownBy(() -> new ProjectSettlementTarget(null, 201L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectSettlementTarget(0L, 201L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유효하지 않은 창작자 식별자를 거부한다")
    void rejectsInvalidCreatorId() {
        assertThatThrownBy(() -> new ProjectSettlementTarget(101L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProjectSettlementTarget(101L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
