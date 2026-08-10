// TODO(settlement-plan): Verify stable refPayoutId reuse, duplicate prevention, retryable failure, and unknown-result blocking.
package com.growmighty.lectures.firstday.settlement.application.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayResult;
import com.growmighty.lectures.firstday.settlement.application.port.payout.ScheduledPayoutRequest;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementFeePolicySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class PayoutExecutionServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T01:02:03Z"),
            ZoneOffset.UTC
    );
    private static final Long SETTLEMENT_ID = 100L;
    private static final Long OBLIGATION_ID = 200L;

    private final List<String> events = new ArrayList<>();
    private InMemoryPayoutObligationRepository payoutObligationRepository;
    private RecordingPayoutGateway payoutGateway;
    private PayoutExecutionService service;

    @BeforeEach
    void setUp() {
        payoutObligationRepository = new InMemoryPayoutObligationRepository(scheduledObligation(), events);
        payoutGateway = new RecordingPayoutGateway(events);
        service = new PayoutExecutionService(
                payoutObligationRepository,
                new InMemoryProjectSettlementRepository(projectSettlement()),
                payoutGateway,
                TransactionOperations.withoutTransaction(),
                CLOCK
        );
    }

    @Test
    @DisplayName("지급 시도를 저장한 뒤 지급대행을 요청하고 접수 결과를 같은 시도에 반영한다")
    void persistsAttemptBeforeCallingGateway() {
        payoutGateway.enqueue(new PayoutGatewayResult.Accepted(
                "dummy-payout-1",
                PayoutAttemptStatus.REQUESTED
        ));

        PayoutExecutionResult result = service.execute(OBLIGATION_ID);

        assertThat(events).containsExactly("save", "gateway", "save");
        assertThat(result.attemptStatus()).isEqualTo(PayoutAttemptStatus.REQUESTED);
        assertThat(result.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.PROCESSING);
        assertThat(payoutObligationRepository.obligation().attempts().getFirst().tossPayoutId())
                .isEqualTo("dummy-payout-1");
        assertThat(payoutGateway.requests().getFirst())
                .extracting(
                        ScheduledPayoutRequest::sellerId,
                        ScheduledPayoutRequest::payoutDate,
                        ScheduledPayoutRequest::amount,
                        ScheduledPayoutRequest::transactionDescription
                )
                .containsExactly(
                        "seller-10",
                        LocalDate.of(2026, 8, 3),
                        Money.wons(91_200),
                        "얼리버드"
                );
    }

    @Test
    @DisplayName("결과 불명확 재실행은 새 시도 없이 같은 참조 식별자와 멱등키를 사용한다")
    void reusesSameAttemptAfterUnknownResult() {
        payoutGateway.enqueue(new PayoutGatewayException("응답을 받지 못했습니다."));
        payoutGateway.enqueue(new PayoutGatewayResult.Accepted(
                "dummy-payout-1",
                PayoutAttemptStatus.IN_PROGRESS
        ));

        PayoutExecutionResult unknown = service.execute(OBLIGATION_ID);
        ScheduledPayoutRequest firstRequest = payoutGateway.requests().getFirst();
        PayoutExecutionResult recovered = service.execute(OBLIGATION_ID);
        ScheduledPayoutRequest secondRequest = payoutGateway.requests().get(1);

        assertThat(unknown.attemptStatus()).isEqualTo(PayoutAttemptStatus.UNKNOWN);
        assertThat(recovered.attemptStatus()).isEqualTo(PayoutAttemptStatus.IN_PROGRESS);
        assertThat(payoutObligationRepository.obligation().attemptCount()).isEqualTo(1);
        assertThat(secondRequest.refPayoutId()).isEqualTo(firstRequest.refPayoutId());
        assertThat(secondRequest.idempotencyKey()).isEqualTo(firstRequest.idempotencyKey());
    }

    @Test
    @DisplayName("최종 일시 오류가 확인된 뒤에만 새 지급 시도와 새 멱등키를 만든다")
    void startsNewAttemptAfterConfirmedRetryableFailure() {
        payoutGateway.enqueue(new PayoutGatewayResult.Failed(
                "dummy-payout-1",
                "DUMMY_RETRYABLE_FAILURE",
                true
        ));
        payoutGateway.enqueue(new PayoutGatewayResult.Accepted(
                "dummy-payout-2",
                PayoutAttemptStatus.REQUESTED
        ));

        PayoutExecutionResult failed = service.execute(OBLIGATION_ID);
        ScheduledPayoutRequest firstRequest = payoutGateway.requests().getFirst();
        PayoutExecutionResult retried = service.execute(OBLIGATION_ID);
        ScheduledPayoutRequest secondRequest = payoutGateway.requests().get(1);

        assertThat(failed.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.RETRY_WAITING);
        assertThat(retried.attemptSequence()).isEqualTo(2);
        assertThat(payoutObligationRepository.obligation().attemptCount()).isEqualTo(2);
        assertThat(secondRequest.refPayoutId()).isNotEqualTo(firstRequest.refPayoutId());
        assertThat(secondRequest.idempotencyKey()).isNotEqualTo(firstRequest.idempotencyKey());
    }

    @Test
    @DisplayName("입력 또는 셀러 조치가 필요한 오류는 자동 재호출하지 않는다")
    void stopsAfterActionRequiredFailure() {
        payoutGateway.enqueue(new PayoutGatewayResult.Failed(
                "dummy-payout-1",
                "DUMMY_ACTION_REQUIRED",
                false
        ));

        PayoutExecutionResult failed = service.execute(OBLIGATION_ID);
        PayoutExecutionResult repeated = service.execute(OBLIGATION_ID);

        assertThat(failed.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.ACTION_REQUIRED);
        assertThat(repeated.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.ACTION_REQUIRED);
        assertThat(payoutGateway.requests()).hasSize(1);
        assertThat(payoutObligationRepository.obligation().attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일시적 지급 실패는 최초 시도와 재시도 3회까지만 허용한다")
    void limitsAutomaticRetryToFourAttempts() {
        for (int count = 0; count < 4; count++) {
            payoutGateway.enqueue(new PayoutGatewayResult.Failed(
                    "dummy-payout-" + count,
                    "DUMMY_RETRYABLE_FAILURE",
                    true
            ));
        }

        PayoutExecutionResult result = null;
        for (int count = 0; count < 4; count++) {
            result = service.execute(OBLIGATION_ID);
        }
        PayoutExecutionResult repeated = service.execute(OBLIGATION_ID);

        assertThat(result).isNotNull();
        assertThat(result.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.ACTION_REQUIRED);
        assertThat(repeated.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.ACTION_REQUIRED);
        assertThat(payoutGateway.requests()).hasSize(4);
        assertThat(payoutObligationRepository.obligation().attemptCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("지급 완료가 확인된 지급 의무는 재실행해도 다시 요청하지 않는다")
    void doesNotRequestCompletedObligationAgain() {
        payoutGateway.enqueue(new PayoutGatewayResult.Accepted(
                "dummy-payout-1",
                PayoutAttemptStatus.COMPLETED
        ));

        PayoutExecutionResult completed = service.execute(OBLIGATION_ID);
        PayoutExecutionResult repeated = service.execute(OBLIGATION_ID);

        assertThat(completed.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.COMPLETED);
        assertThat(repeated.payoutObligationStatus()).isEqualTo(PayoutObligationStatus.COMPLETED);
        assertThat(payoutGateway.requests()).hasSize(1);
    }

    private static PayoutObligation scheduledObligation() {
        return PayoutObligation.restore(
                OBLIGATION_ID,
                SETTLEMENT_ID,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3),
                PayoutObligationStatus.SCHEDULED,
                List.of(),
                null,
                0L
        );
    }

    private static ProjectSettlement projectSettlement() {
        return ProjectSettlement.restore(
                SETTLEMENT_ID,
                1L,
                10L,
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
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDate.of(2026, 8, 3),
                PayoutStatus.SCHEDULED,
                LocalDateTime.of(2026, 7, 26, 1, 0)
        );
    }

    private static final class InMemoryPayoutObligationRepository
            implements PayoutObligationRepository {

        private PayoutObligation obligation;
        private final List<String> events;

        private InMemoryPayoutObligationRepository(
                PayoutObligation obligation,
                List<String> events
        ) {
            this.obligation = obligation;
            this.events = events;
        }

        @Override
        public PayoutObligation save(PayoutObligation obligation) {
            events.add("save");
            this.obligation = obligation;
            return obligation;
        }

        @Override
        public Optional<PayoutObligation> findById(Long id) {
            return Objects.equals(obligation.id(), id) ? Optional.of(obligation) : Optional.empty();
        }

        @Override
        public Optional<PayoutObligation> findBySettlementId(Long settlementId) {
            return Objects.equals(obligation.settlementId(), settlementId)
                    ? Optional.of(obligation)
                    : Optional.empty();
        }

        private PayoutObligation obligation() {
            return obligation;
        }
    }

    private record InMemoryProjectSettlementRepository(ProjectSettlement settlement)
            implements ProjectSettlementRepository {

        @Override
        public ProjectSettlement save(ProjectSettlement settlement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProjectSettlement> findById(Long id) {
            return Objects.equals(settlement.id(), id) ? Optional.of(settlement) : Optional.empty();
        }

        @Override
        public Optional<ProjectSettlement> findByProjectId(Long projectId) {
            return Objects.equals(settlement.projectId(), projectId)
                    ? Optional.of(settlement)
                    : Optional.empty();
        }

        @Override
        public List<ProjectSettlement> findAllByCreatorIdOrderByConfirmedAtDescIdDesc(Long creatorId) {
            return settlement.creatorId().equals(creatorId) ? List.of(settlement) : List.of();
        }

        @Override
        public List<ProjectSettlement> findAllByOrderByConfirmedAtDescIdDesc() {
            return List.of(settlement);
        }
    }

    private static final class RecordingPayoutGateway implements PayoutGateway {

        private final List<String> events;
        private final Deque<Object> outcomes = new ArrayDeque<>();
        private final List<ScheduledPayoutRequest> requests = new ArrayList<>();

        private RecordingPayoutGateway(List<String> events) {
            this.events = events;
        }

        @Override
        public PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request) {
            events.add("gateway");
            requests.add(request);
            Object outcome = outcomes.removeFirst();
            if (outcome instanceof PayoutGatewayException exception) {
                throw exception;
            }
            return (PayoutGatewayResult) outcome;
        }

        private void enqueue(Object outcome) {
            outcomes.addLast(outcome);
        }

        private List<ScheduledPayoutRequest> requests() {
            return requests;
        }
    }
}
