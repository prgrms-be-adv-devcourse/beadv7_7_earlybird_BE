package com.growmighty.lectures.firstday.settlement.application.run;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.settlement.ConfirmedProjectSettlement;
import com.growmighty.lectures.firstday.settlement.application.settlement.ProjectSettlementService;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProjectSettlementRunServiceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("Order의 주문별 결제금액으로 성공 프로젝트를 정산한다")
    void settlesSucceededProjectFromOrderPaymentAmounts() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(510L));
        ProjectSettlementRunService service = service(
                List.of(new ProjectOutcome(510L, 510L, ProjectOutcomeStatus.SUCCEEDED)),
                List.of(new ProjectOrders(510L, List.of(
                        new OrderPayment(5_101L, Money.wons(40_000)),
                        new OrderPayment(5_102L, Money.wons(60_000))
                )))
        );

        ProjectSettlementRunResult result = service.run(command());

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("실패·취소 프로젝트는 월 실행에서 환불 요청 대기로만 분류한다")
    void leavesRefundProjectsToOutboxFlow() {
        AtomicBoolean orderRead = new AtomicBoolean();
        ProjectSettlementRunService service = new ProjectSettlementRunService(
                () -> List.of(
                        new ProjectOutcome(520L, 520L, ProjectOutcomeStatus.FAILED),
                        new ProjectOutcome(521L, 521L, ProjectOutcomeStatus.CANCELLED)
                ),
                projectIds -> {
                    orderRead.set(true);
                    return List.of();
                },
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = service.run(command());

        assertThat(orderRead).isFalse();
        assertThat(result.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(
                        tuple(520L, ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING),
                        tuple(521L, ProjectOutcomeProcessingStatus.REFUND_REQUEST_PENDING)
                );
    }

    @Test
    @DisplayName("성공 프로젝트의 Order 결과가 누락되면 전체 실행을 거부한다")
    void rejectsMissingProjectOrderResult() {
        ProjectSettlementRunService service = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(530L, 530L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(),
                projectSettlementService,
                fixedClock()
        );

        assertThatThrownBy(() -> service.run(command()))
                .isInstanceOfSatisfying(
                        SettlementException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ORDER_PAYMENT_INPUTS_UNAVAILABLE)
                );
    }

    @Test
    @DisplayName("이미 확정된 성공 프로젝트는 Order를 다시 조회하지 않는다")
    void restoresExistingSettlementWithoutOrderRead() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(540L));
        ProjectSettlementRunService initial = service(
                List.of(new ProjectOutcome(540L, 540L, ProjectOutcomeStatus.SUCCEEDED)),
                List.of(projectOrders(540L, 5_401L))
        );
        initial.run(command());
        AtomicBoolean orderRead = new AtomicBoolean();
        ProjectSettlementRunService rerun = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(540L, 540L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> {
                    orderRead.set(true);
                    return List.of();
                },
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = rerun.run(command());

        assertThat(orderRead).isFalse();
        assertThat(result.projectResults().getFirst().processingStatus())
                .isEqualTo(ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED);
    }

    private ProjectSettlementRunService service(
            List<ProjectOutcome> outcomes,
            List<ProjectOrders> orders
    ) {
        return new ProjectSettlementRunService(
                () -> outcomes,
                projectIds -> orders,
                projectSettlementService,
                fixedClock()
        );
    }

    private static ProjectOrders projectOrders(Long projectId, Long orderId) {
        return new ProjectOrders(
                projectId,
                List.of(new OrderPayment(orderId, Money.wons(100_000)))
        );
    }

    private static RunProjectSettlementsCommand command() {
        return new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
    }

    private static CreatorPayoutProfile payoutReadyProfile(Long creatorId) {
        return CreatorPayoutProfile.registered(
                creatorId,
                "seller-" + creatorId,
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********" + creatorId,
                LocalDateTime.of(2026, 7, 23, 9, 0)
        );
    }
}
