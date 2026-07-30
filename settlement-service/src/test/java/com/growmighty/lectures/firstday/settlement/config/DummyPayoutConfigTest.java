package com.growmighty.lectures.firstday.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DummyPayoutConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DummyPayoutConfig.class);

    @Test
    @DisplayName("별도 설정이 없어도 완료 시나리오의 더미 지급대행을 등록한다")
    void registersCompletedDummyByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PayoutGateway.class);
            assertThat(status(context.getBean(PayoutGateway.class)))
                    .isEqualTo(PayoutAttemptStatus.COMPLETED);
        });
    }

    @Test
    @DisplayName("명시적 로컬 설정으로 더미 지급 시나리오를 선택한다")
    void selectsConfiguredScenario() {
        contextRunner
                .withPropertyValues("settlement.dummy-payout.scenario=in-progress")
                .run(context -> assertThat(status(context.getBean(PayoutGateway.class)))
                        .isEqualTo(PayoutAttemptStatus.IN_PROGRESS));
    }

    private static PayoutAttemptStatus status(PayoutGateway gateway) {
        PayoutGatewayResult result = gateway.requestScheduledPayout(new ScheduledPayoutRequest(
                "earlybird-payout-1-1",
                "dummy-seller-1",
                LocalDate.of(2026, 8, 3),
                Money.wons(91_200),
                "얼리버드",
                "idempotency-1"
        ));
        return ((PayoutGatewayResult.Accepted) result).status();
    }
}
