// TODO(settlement-plan): Verify settlement payout state persists before gateway I/O.
package com.growmighty.lectures.firstday.settlement.application.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.payout.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@Import(PayoutExecutionTransactionTest.GatewayTestConfig.class)
class PayoutExecutionTransactionTest extends MySqlIntegrationTestSupport {

    @Autowired
    private PayoutExecutor payoutExecutor;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private PayoutObligationRepository payoutObligationRepository;

    @Autowired
    private ObservingPayoutGateway payoutGateway;

    @Test
    @DisplayName("지급 시도 선저장 트랜잭션을 커밋한 뒤 지급대행을 요청한다")
    void commitsAttemptBeforeExternalCall() {
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                501L,
                601L,
                List.of(Money.wons(100_000)),
                LocalDateTime.of(2026, 7, 26, 10, 0)
        ));
        payoutObligationRepository.save(PayoutObligation.schedule(
                settlement,
                CreatorPayoutProfile.registered(
                        601L,
                        "seller-601",
                        CreatorPayoutStatus.PAYOUT_READY
                ),
                LocalDate.of(2026, 8, 3)
        ));
        payoutGateway.observe(settlement.id());

        PayoutExecutionResult result = payoutExecutor.execute(settlement.id());

        assertThat(payoutGateway.transactionActiveDuringCall()).isFalse();
        assertThat(payoutGateway.observedAttemptStatus()).isEqualTo(PayoutAttemptStatus.REQUESTED);
        assertThat(result.payoutStatus()).isEqualTo(PayoutStatus.PROCESSING);
        PayoutObligation restored = payoutObligationRepository.findBySettlementId(settlement.id()).orElseThrow();
        assertThat(restored.attemptCount()).isEqualTo(1);
        assertThat(restored.attempts().getFirst().tossPayoutId()).isEqualTo("dummy-payout-501");
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
        private Long expectedSettlementId;
        private boolean transactionActiveDuringCall;
        private PayoutAttemptStatus observedAttemptStatus;

        private ObservingPayoutGateway(PayoutObligationRepository payoutObligationRepository) {
            this.payoutObligationRepository = payoutObligationRepository;
        }

        @Override
        public PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request) {
            transactionActiveDuringCall = TransactionSynchronizationManager
                    .isActualTransactionActive();
            PayoutObligation persisted = payoutObligationRepository.findBySettlementId(expectedSettlementId)
                    .orElseThrow();
            observedAttemptStatus = persisted.attempts().getFirst().status();
            return new PayoutGatewayResult.Accepted(
                    "dummy-payout-501",
                    PayoutAttemptStatus.REQUESTED
            );
        }

        private void observe(Long settlementId) {
            this.expectedSettlementId = settlementId;
        }

        private boolean transactionActiveDuringCall() {
            return transactionActiveDuringCall;
        }

        private PayoutAttemptStatus observedAttemptStatus() {
            return observedAttemptStatus;
        }
    }
}
