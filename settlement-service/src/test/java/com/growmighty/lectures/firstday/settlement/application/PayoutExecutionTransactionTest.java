package com.growmighty.lectures.firstday.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
        "settlement.toss-payout.enabled=true",
        "settlement.toss-payout.secret-key=test_sk_example",
        "settlement.toss-payout.security-key=0101010101010101010101010101010101010101010101010101010101010101"
})
@Import(PayoutExecutionTransactionTest.GatewayTestConfig.class)
class PayoutExecutionTransactionTest {

    @Autowired
    private PayoutExecutor payoutExecutor;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private PayoutObligationRepository payoutObligationRepository;

    @Autowired
    private ObservingPayoutGateway payoutGateway;

    @Test
    @DisplayName("지급 시도 선저장 트랜잭션을 커밋한 뒤 외부 지급을 요청한다")
    void commitsAttemptBeforeExternalCall() {
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                501L,
                "지급 트랜잭션 테스트 프로젝트",
                601L,
                SettlementFeePolicySnapshot.current(),
                SettlementBreakdown.of(
                        Money.wons(100_000),
                        Money.wons(4_000),
                        Money.wons(400),
                        Money.wons(4_000),
                        Money.wons(400),
                        Money.wons(0),
                        Money.wons(91_200)
                ),
                PayoutDestinationSnapshot.of(601L, "seller-601", "088", "********0601"),
                LocalDateTime.of(2026, 7, 26, 10, 0)
        ));
        PayoutObligation obligation = payoutObligationRepository.save(PayoutObligation.schedule(
                settlement.id(),
                settlement.creatorId(),
                settlement.creatorPayoutAmount(),
                LocalDate.of(2026, 8, 3)
        ));
        payoutGateway.observe(obligation.id());

        PayoutExecutionResult result = payoutExecutor.execute(obligation.id());

        assertThat(payoutGateway.transactionActiveDuringCall()).isFalse();
        assertThat(payoutGateway.observedAttemptStatus()).isEqualTo(PayoutAttemptStatus.REQUESTED);
        assertThat(result.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.PROCESSING);
        PayoutObligation restored = payoutObligationRepository.findById(obligation.id()).orElseThrow();
        assertThat(restored.attemptCount()).isEqualTo(1);
        assertThat(restored.attempts().getFirst().tossPayoutId()).isEqualTo("toss-payout-501");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GatewayTestConfig {

        @Bean
        @Primary
        ObservingPayoutGateway observingPayoutGateway(
                PayoutObligationRepository payoutObligationRepository
        ) {
            return new ObservingPayoutGateway(payoutObligationRepository);
        }
    }

    static final class ObservingPayoutGateway implements PayoutGateway {

        private final PayoutObligationRepository payoutObligationRepository;
        private Long expectedObligationId;
        private boolean transactionActiveDuringCall;
        private PayoutAttemptStatus observedAttemptStatus;

        private ObservingPayoutGateway(PayoutObligationRepository payoutObligationRepository) {
            this.payoutObligationRepository = payoutObligationRepository;
        }

        @Override
        public PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request) {
            transactionActiveDuringCall = TransactionSynchronizationManager
                    .isActualTransactionActive();
            PayoutObligation persisted = payoutObligationRepository.findById(expectedObligationId)
                    .orElseThrow();
            observedAttemptStatus = persisted.attempts().getFirst().status();
            return new PayoutGatewayResult.Accepted(
                    "toss-payout-501",
                    PayoutAttemptStatus.REQUESTED,
                    null
            );
        }

        private void observe(Long payoutObligationId) {
            this.expectedObligationId = payoutObligationId;
        }

        private boolean transactionActiveDuringCall() {
            return transactionActiveDuringCall;
        }

        private PayoutAttemptStatus observedAttemptStatus() {
            return observedAttemptStatus;
        }
    }
}
