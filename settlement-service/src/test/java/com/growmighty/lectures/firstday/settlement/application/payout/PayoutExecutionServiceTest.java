package com.growmighty.lectures.firstday.settlement.application.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGatewayException;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class PayoutExecutionServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T01:02:03Z"),
            ZoneOffset.UTC
    );
    private static final Long SETTLEMENT_ID = 100L;

    private final List<String> events = new ArrayList<>();
    private InMemoryPayoutObligationRepository payoutObligationRepository;
    private RecordingPayoutGateway payoutGateway;
    private PayoutExecutionService service;

    @BeforeEach
    void setUp() {
        payoutObligationRepository = new InMemoryPayoutObligationRepository(payoutObligation(), events);
        payoutGateway = new RecordingPayoutGateway(events);
        service = new PayoutExecutionService(
                payoutObligationRepository,
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

        PayoutExecutionResult result = service.execute(SETTLEMENT_ID);

        assertThat(events).containsExactly("save", "gateway", "save");
        assertThat(result.attemptStatus()).isEqualTo(PayoutAttemptStatus.REQUESTED);
        assertThat(result.payoutStatus()).isEqualTo(PayoutStatus.PROCESSING);
        assertThat(payoutObligationRepository.payoutObligation().attempts().getFirst().tossPayoutId())
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

        PayoutExecutionResult unknown = service.execute(SETTLEMENT_ID);
        ScheduledPayoutRequest firstRequest = payoutGateway.requests().getFirst();
        PayoutExecutionResult recovered = service.execute(SETTLEMENT_ID);
        ScheduledPayoutRequest secondRequest = payoutGateway.requests().get(1);

        assertThat(unknown.attemptStatus()).isEqualTo(PayoutAttemptStatus.UNKNOWN);
        assertThat(recovered.attemptStatus()).isEqualTo(PayoutAttemptStatus.IN_PROGRESS);
        assertThat(payoutObligationRepository.payoutObligation().attemptCount()).isEqualTo(1);
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

        PayoutExecutionResult failed = service.execute(SETTLEMENT_ID);
        ScheduledPayoutRequest firstRequest = payoutGateway.requests().getFirst();
        PayoutExecutionResult retried = service.execute(SETTLEMENT_ID);
        ScheduledPayoutRequest secondRequest = payoutGateway.requests().get(1);

        assertThat(failed.payoutStatus()).isEqualTo(PayoutStatus.RETRY_WAITING);
        assertThat(retried.attemptSequence()).isEqualTo(2);
        assertThat(payoutObligationRepository.payoutObligation().attemptCount()).isEqualTo(2);
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

        PayoutExecutionResult failed = service.execute(SETTLEMENT_ID);
        PayoutExecutionResult repeated = service.execute(SETTLEMENT_ID);

        assertThat(failed.payoutStatus()).isEqualTo(PayoutStatus.ACTION_REQUIRED);
        assertThat(repeated.payoutStatus()).isEqualTo(PayoutStatus.ACTION_REQUIRED);
        assertThat(payoutGateway.requests()).hasSize(1);
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
            result = service.execute(SETTLEMENT_ID);
        }
        PayoutExecutionResult repeated = service.execute(SETTLEMENT_ID);

        assertThat(result).isNotNull();
        assertThat(result.payoutStatus()).isEqualTo(PayoutStatus.ACTION_REQUIRED);
        assertThat(repeated.payoutStatus()).isEqualTo(PayoutStatus.ACTION_REQUIRED);
        assertThat(payoutGateway.requests()).hasSize(4);
    }

    @Test
    @DisplayName("지급 완료가 확인된 프로젝트 정산은 재실행해도 다시 요청하지 않는다")
    void doesNotRequestCompletedSettlementAgain() {
        payoutGateway.enqueue(new PayoutGatewayResult.Accepted(
                "dummy-payout-1",
                PayoutAttemptStatus.COMPLETED
        ));

        PayoutExecutionResult completed = service.execute(SETTLEMENT_ID);
        PayoutExecutionResult repeated = service.execute(SETTLEMENT_ID);

        assertThat(completed.payoutStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(repeated.payoutStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payoutGateway.requests()).hasSize(1);
    }

    private static PayoutObligation payoutObligation() {
        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L,
                10L,
                List.of(Money.wons(100_000)),
                LocalDateTime.of(2026, 7, 26, 1, 0)
        );
        ReflectionTestUtils.setField(settlement, "id", SETTLEMENT_ID);
        return PayoutObligation.schedule(
                settlement,
                CreatorPayoutProfile.registered(
                        10L,
                        "seller-10",
                        CreatorPayoutStatus.PAYOUT_READY
                ),
                LocalDate.of(2026, 8, 3)
        );
    }

    private static final class InMemoryPayoutObligationRepository implements PayoutObligationRepository {

        private PayoutObligation payoutObligation;
        private final List<String> events;

        private InMemoryPayoutObligationRepository(PayoutObligation payoutObligation, List<String> events) {
            this.payoutObligation = payoutObligation;
            this.events = events;
        }

        @Override
        public PayoutObligation save(PayoutObligation payoutObligation) {
            events.add("save");
            this.payoutObligation = payoutObligation;
            return payoutObligation;
        }

        @Override
        public Optional<PayoutObligation> findById(Long id) {
            return Objects.equals(payoutObligation.id(), id) ? Optional.of(payoutObligation) : Optional.empty();
        }

        @Override
        public Optional<PayoutObligation> findBySettlementId(Long settlementId) {
            return Objects.equals(payoutObligation.settlementId(), settlementId) ? Optional.of(payoutObligation) : Optional.empty();
        }

        @Override
        public List<PayoutObligation> findAllBySettlementIdIn(java.util.Collection<Long> settlementIds) {
            return settlementIds.contains(payoutObligation.settlementId()) ? List.of(payoutObligation) : List.of();
        }

        private PayoutObligation payoutObligation() {
            return payoutObligation;
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
