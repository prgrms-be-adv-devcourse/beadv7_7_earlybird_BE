package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.port.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectCancellationReason.PROJECT_FAILED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.COMPLETED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.PROCESSING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessment;
import com.growmighty.lectures.firstday.settlement.application.port.PaymentAssessmentReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProjectSettlementRunServiceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private ProjectSettlementRunService connectedRunService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("Order의 주문 식별자와 Payment의 결제 판정으로 성공 프로젝트를 정산한다")
    void settlesSucceededProjectFromOrderAndPaymentInputs() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(210L, "seller-210", "********0210"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(110L, 210L, ProjectOutcomeStatus.SUCCEEDED)
        );
        ProjectOrderReader orderReader = projectIds -> List.of(
                new ProjectOrders(110L, List.of(1_001L, 1_002L))
        );
        PaymentAssessmentReader paymentReader = orderIds -> List.of(
                PaymentAssessment.ready(1_001L, Money.wons(40_000)),
                PaymentAssessment.ready(1_002L, Money.wons(60_000))
        );
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                noCancellationExpected(),
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );

        ProjectSettlementRunResult result = runService.run(new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        ));

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("결제가 없는 주문은 0원 항목으로 보존하고 준비된 결제만 정산 금액에 반영한다")
    void treatsNoPaymentAsZeroAmountItem() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(214L, "seller-214", "********0214"));
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(114L, 214L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(new ProjectOrders(114L, List.of(1_003L, 1_004L))),
                orderIds -> List.of(
                        PaymentAssessment.noPayment(1_003L),
                        PaymentAssessment.ready(1_004L, Money.wons(100_000))
                ),
                noCancellationExpected(),
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("준비되지 않은 성공 프로젝트는 보류하고 다음 준비 완료 프로젝트를 정산한다")
    void defersNotReadyProjectAndContinuesReadySettlement() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(216L, "seller-216", "********0216"));
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(
                        new ProjectOutcome(115L, 215L, ProjectOutcomeStatus.SUCCEEDED),
                        new ProjectOutcome(116L, 216L, ProjectOutcomeStatus.SUCCEEDED)
                ),
                projectIds -> List.of(
                        new ProjectOrders(115L, List.of(1_005L)),
                        new ProjectOrders(116L, List.of(1_006L))
                ),
                orderIds -> List.of(
                        PaymentAssessment.notReady(1_005L),
                        PaymentAssessment.ready(1_006L, Money.wons(100_000))
                ),
                noCancellationExpected(),
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(
                        tuple(115L, ProjectOutcomeProcessingStatus.PAYMENT_NOT_READY),
                        tuple(116L, ProjectOutcomeProcessingStatus.SETTLEMENT_CONFIRMED)
                );
        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::projectId)
                .containsExactly(116L);
    }

    @Test
    @DisplayName("성공 주문만 결제 판정을 조회하고 실패·취소 주문은 결제 취소를 요청한다")
    void routesProjectOutcomesToSettlementOrPaymentCancellation() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(211L, "seller-211", "********0211"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(111L, 211L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(112L, 212L, ProjectOutcomeStatus.FAILED),
                new ProjectOutcome(113L, 213L, ProjectOutcomeStatus.CANCELLED)
        );
        AtomicReference<Set<Long>> requestedProjectIds = new AtomicReference<>();
        ProjectOrderReader orderReader = projectIds -> {
            requestedProjectIds.set(projectIds);
            return List.of(
                    new ProjectOrders(111L, List.of(1_001L)),
                    new ProjectOrders(112L, List.of(1_002L)),
                    new ProjectOrders(113L, List.of(1_003L))
            );
        };
        AtomicReference<Set<Long>> assessedOrderIds = new AtomicReference<>();
        PaymentAssessmentReader paymentReader = orderIds -> {
            assessedOrderIds.set(orderIds);
            return List.of(PaymentAssessment.ready(1_001L, Money.wons(100_000)));
        };
        AtomicReference<List<ProjectPaymentCancellationRequest>> cancellationRequests =
                new AtomicReference<>();
        ProjectPaymentCancellationGateway cancellationGateway = requests -> {
            cancellationRequests.set(requests);
            return List.of(
                    new ProjectPaymentCancellationResult(1_002L, COMPLETED),
                    new ProjectPaymentCancellationResult(1_003L, PROCESSING)
            );
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                cancellationGateway,
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(requestedProjectIds.get()).containsExactlyInAnyOrder(111L, 112L, 113L);
        assertThat(assessedOrderIds.get()).containsExactly(1_001L);
        assertThat(cancellationRequests.get())
                .extracting(
                        ProjectPaymentCancellationRequest::orderId,
                        ProjectPaymentCancellationRequest::reason
                )
                .containsExactly(
                        tuple(1_002L, PROJECT_FAILED),
                        tuple(1_003L, PROJECT_CANCELLED)
                );
        assertThat(cancellationRequests.get())
                .extracting(ProjectPaymentCancellationRequest::idempotencyKey)
                .allSatisfy(idempotencyKey -> assertThat(idempotencyKey).isNotBlank())
                .doesNotHaveDuplicates();
        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::projectId)
                .containsExactly(111L);
        assertThat(result.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::outcomeStatus,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(
                        tuple(
                                111L,
                                ProjectOutcomeStatus.SUCCEEDED,
                                ProjectOutcomeProcessingStatus.SETTLEMENT_CONFIRMED
                        ),
                        tuple(
                                112L,
                                ProjectOutcomeStatus.FAILED,
                                ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED
                        ),
                        tuple(
                                113L,
                                ProjectOutcomeStatus.CANCELLED,
                                ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_PROCESSING
                        )
                );
    }

    @Test
    @DisplayName("주문이 없는 실패 프로젝트는 Payment를 호출하지 않고 결제 취소 완료로 처리한다")
    void completesFailedProjectWithoutOrdersAsNoOp() {
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(117L, 217L, ProjectOutcomeStatus.FAILED)),
                projectIds -> List.of(new ProjectOrders(117L, List.of())),
                orderIds -> {
                    throw new AssertionError("실패 프로젝트의 결제 판정을 조회하면 안 됩니다.");
                },
                requests -> {
                    throw new AssertionError("빈 결제 취소 목록을 호출하면 안 됩니다.");
                },
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(tuple(
                        117L,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED
                ));
        assertThat(result.confirmedSettlements()).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("cancellationStatusMappings")
    @DisplayName("Payment의 주문별 결제 취소 결과를 프로젝트 처리 상태로 변환한다")
    void mapsPaymentCancellationStatusToProjectResult(
            ProjectPaymentCancellationStatus paymentStatus,
            ProjectOutcomeProcessingStatus expectedStatus
    ) {
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(118L, 218L, ProjectOutcomeStatus.FAILED)),
                projectIds -> List.of(new ProjectOrders(118L, List.of(1_007L))),
                orderIds -> {
                    throw new AssertionError("실패 프로젝트의 결제 판정을 조회하면 안 됩니다.");
                },
                requests -> List.of(new ProjectPaymentCancellationResult(1_007L, paymentStatus)),
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(expectedStatus);
    }

    @Test
    @DisplayName("같은 프로젝트 결과의 결제 취소 재호출은 주문별 멱등키를 유지한다")
    void reusesStableCancellationIdempotencyKey() {
        List<String> observedIdempotencyKeys = new ArrayList<>();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(119L, 219L, ProjectOutcomeStatus.CANCELLED)),
                projectIds -> List.of(new ProjectOrders(119L, List.of(1_008L))),
                orderIds -> {
                    throw new AssertionError("취소 프로젝트의 결제 판정을 조회하면 안 됩니다.");
                },
                requests -> {
                    observedIdempotencyKeys.add(requests.getFirst().idempotencyKey());
                    return List.of(new ProjectPaymentCancellationResult(1_008L, COMPLETED));
                },
                projectSettlementService,
                fixedClock()
        );

        runService.run(command());
        runService.run(command());

        assertThat(observedIdempotencyKeys)
                .hasSize(2)
                .allSatisfy(idempotencyKey -> assertThat(idempotencyKey).isNotBlank());
        assertThat(observedIdempotencyKeys.getLast())
                .isEqualTo(observedIdempotencyKeys.getFirst());
    }

    @Test
    @DisplayName("주문 일부의 결제 취소 결과가 불명확하면 프로젝트를 완료로 추정하지 않는다")
    void preservesUnknownPartialCancellationResult() {
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(120L, 220L, ProjectOutcomeStatus.FAILED)),
                projectIds -> List.of(new ProjectOrders(120L, List.of(1_009L, 1_010L))),
                orderIds -> {
                    throw new AssertionError("실패 프로젝트의 결제 판정을 조회하면 안 됩니다.");
                },
                requests -> List.of(
                        new ProjectPaymentCancellationResult(1_009L, COMPLETED),
                        new ProjectPaymentCancellationResult(
                                1_010L,
                                ProjectPaymentCancellationStatus.UNKNOWN
                        )
                ),
                projectSettlementService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_UNKNOWN);
    }

    @Test
    @DisplayName("대상 월의 모든 프로젝트 정산을 실행한다")
    void runsAllProjectSettlementsForMonth() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(201L, "seller-201", "********0201"));
        creatorPayoutProfileRepository.save(payoutReadyProfile(202L, "seller-202", "********0202"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(101L, 201L, ProjectOutcomeStatus.SUCCEEDED),
                new ProjectOutcome(102L, 202L, ProjectOutcomeStatus.SUCCEEDED)
        );
        Map<Long, Money> amountsByOrder = Map.of(
                101L, Money.wons(100_000),
                102L, Money.wons(200_000)
        );
        ProjectOrderReader orderReader = sameIdProjectOrderReader();
        PaymentAssessmentReader paymentReader = orderIds -> orderIds.stream()
                .map(orderId -> PaymentAssessment.ready(orderId, amountsByOrder.get(orderId)))
                .toList();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                noCancellationExpected(),
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );
        RunProjectSettlementsCommand command = new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ProjectSettlementRunResult result = runService.run(command);

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200), Money.wons(182_400));
    }

    @Test
    @DisplayName("더미 외부 정보로 프로젝트 정산을 실행한다")
    void runsWithDummyExternalInformation() {
        RunProjectSettlementsCommand command = new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ProjectSettlementRunResult result = connectedRunService.run(command);

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("같은 월에 같은 프로젝트를 다시 조회해도 기존 프로젝트 정산을 유지한다")
    void keepsExistingSettlementWhenMonthRunIsRepeated() {
        long projectId = 103L;
        long creatorId = 203L;
        creatorPayoutProfileRepository.save(
                payoutReadyProfile(creatorId, "seller-203", "********0203")
        );
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(projectId, creatorId, ProjectOutcomeStatus.SUCCEEDED)
        );
        AtomicInteger paymentReads = new AtomicInteger();
        ProjectOrderReader orderReader = sameIdProjectOrderReader();
        PaymentAssessmentReader paymentReader = orderIds -> {
            Money amount = paymentReads.getAndIncrement() == 0
                    ? Money.wons(100_000)
                    : Money.wons(900_000);
            return orderIds.stream()
                    .map(orderId -> PaymentAssessment.ready(orderId, amount))
                    .toList();
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                paymentReader,
                noCancellationExpected(),
                projectSettlementService,
                Clock.fixed(
                        LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                        ZoneOffset.UTC
                )
        );
        RunProjectSettlementsCommand command = new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );

        ProjectSettlementRunResult first = runService.run(command);
        ProjectSettlementRunResult second = runService.run(command);

        assertThat(paymentReads).hasValue(2);
        assertThat(first.confirmedSettlements())
                .containsExactlyElementsOf(second.confirmedSettlements());
        assertThat(second.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    private static ProjectOrderReader sameIdProjectOrderReader() {
        return projectIds -> projectIds.stream()
                .map(projectId -> new ProjectOrders(projectId, List.of(projectId)))
                .toList();
    }

    private static Stream<Arguments> cancellationStatusMappings() {
        return Stream.of(
                Arguments.of(
                        ProjectPaymentCancellationStatus.COMPLETED,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED
                ),
                Arguments.of(
                        ProjectPaymentCancellationStatus.ALREADY_COMPLETED,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED
                ),
                Arguments.of(
                        ProjectPaymentCancellationStatus.NO_REFUND_REQUIRED,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED
                ),
                Arguments.of(
                        ProjectPaymentCancellationStatus.PROCESSING,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_PROCESSING
                ),
                Arguments.of(
                        ProjectPaymentCancellationStatus.RETRYABLE_FAILED,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_RETRYABLE_FAILED
                ),
                Arguments.of(
                        ProjectPaymentCancellationStatus.FINAL_FAILED,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_FINAL_FAILED
                ),
                Arguments.of(
                        ProjectPaymentCancellationStatus.UNKNOWN,
                        ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_UNKNOWN
                )
        );
    }

    private static ProjectPaymentCancellationGateway noCancellationExpected() {
        return requests -> {
            throw new AssertionError("성공 프로젝트에서 결제 취소를 요청하면 안 됩니다.");
        };
    }

    private static RunProjectSettlementsCommand command() {
        return new RunProjectSettlementsCommand(
                YearMonth.of(2026, 7),
                LocalDate.of(2026, 8, 3),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(
                LocalDateTime.of(2026, 7, 23, 10, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC
        );
    }

    private static CreatorPayoutProfile payoutReadyProfile(
            Long creatorId,
            String sellerId,
            String maskedAccountNumber
    ) {
        return CreatorPayoutProfile.registered(
                creatorId,
                sellerId,
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                maskedAccountNumber,
                LocalDateTime.of(2026, 7, 23, 9, 0)
        );
    }
}
