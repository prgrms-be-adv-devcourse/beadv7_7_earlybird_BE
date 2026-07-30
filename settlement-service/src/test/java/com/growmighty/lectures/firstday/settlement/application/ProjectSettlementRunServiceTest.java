package com.growmighty.lectures.firstday.settlement.application;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.ORDER_PAYMENT_INPUTS_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.COMPLETED;
import static com.growmighty.lectures.firstday.settlement.application.port.ProjectPaymentCancellationStatus.PROCESSING;
import static com.growmighty.lectures.firstday.settlement.domain.ProjectCancellationReason.PROJECT_CANCELLED;
import static com.growmighty.lectures.firstday.settlement.domain.ProjectCancellationReason.PROJECT_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.OrderPayment;
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
import com.growmighty.lectures.firstday.settlement.domain.ProjectPaymentCancellationCommand;
import com.growmighty.lectures.firstday.settlement.domain.ProjectPaymentCancellationCommandStatus;
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
import org.junit.jupiter.params.provider.EnumSource;
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
    private ProjectPaymentCancellationCommandService cancellationCommandService;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Test
    @DisplayName("Order의 주문별 결제금액으로 성공 프로젝트를 정산한다")
    void settlesSucceededProjectFromOrderPaymentAmounts() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(210L, "seller-210", "********0210"));
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(110L, 210L, ProjectOutcomeStatus.SUCCEEDED)
        );
        ProjectOrderReader orderReader = projectIds -> List.of(
                new ProjectOrders(110L, List.of(
                        new OrderPayment(1_001L, Money.wons(40_000)),
                        new OrderPayment(1_002L, Money.wons(60_000))
                ))
        );
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
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
    @DisplayName("Order의 0원 주문 항목과 유상 주문 항목을 함께 정산한다")
    void settlesZeroAmountAndPaidOrderItemsTogether() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(214L, "seller-214", "********0214"));
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(114L, 214L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(new ProjectOrders(114L, List.of(
                        new OrderPayment(1_003L, Money.wons(0)),
                        new OrderPayment(1_004L, Money.wons(100_000))
                ))),
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
    }

    @Test
    @DisplayName("요청한 프로젝트의 Order 결과가 누락되면 계약 오류로 거부한다")
    void rejectsMissingProjectOrderResult() {
        assertOrderContractViolation(
                () -> List.of(
                        new ProjectOutcome(301L, 401L, ProjectOutcomeStatus.SUCCEEDED),
                        new ProjectOutcome(302L, 402L, ProjectOutcomeStatus.SUCCEEDED)
                ),
                projectIds -> List.of(projectOrders(301L, 3_001L))
        );
    }

    @Test
    @DisplayName("Order가 같은 프로젝트 결과를 중복 반환하면 계약 오류로 거부한다")
    void rejectsDuplicateProjectOrderResult() {
        assertOrderContractViolation(
                () -> List.of(new ProjectOutcome(303L, 403L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(
                        projectOrders(303L, 3_002L),
                        projectOrders(303L, 3_003L)
                )
        );
    }

    @Test
    @DisplayName("Order가 요청하지 않은 프로젝트 결과를 반환하면 계약 오류로 거부한다")
    void rejectsUnexpectedProjectOrderResult() {
        assertOrderContractViolation(
                () -> List.of(new ProjectOutcome(304L, 404L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(projectOrders(999L, 3_004L))
        );
    }

    @Test
    @DisplayName("여러 프로젝트에 같은 주문 식별자가 포함되면 계약 오류로 거부한다")
    void rejectsDuplicateOrderIdAcrossProjects() {
        assertOrderContractViolation(
                () -> List.of(
                        new ProjectOutcome(305L, 405L, ProjectOutcomeStatus.SUCCEEDED),
                        new ProjectOutcome(306L, 406L, ProjectOutcomeStatus.SUCCEEDED)
                ),
                projectIds -> List.of(
                        projectOrders(305L, 3_005L),
                        projectOrders(306L, 3_005L)
                )
        );
    }

    @Test
    @DisplayName("성공 프로젝트의 주문 목록이 비어 있으면 계약 오류로 거부한다")
    void rejectsEmptyOrdersForSuccessfulProject() {
        assertOrderContractViolation(
                () -> List.of(new ProjectOutcome(307L, 407L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(projectOrders(307L))
        );
    }

    @Test
    @DisplayName("성공 프로젝트의 주문 결제금액 합계가 0원이면 계약 오류로 거부한다")
    void rejectsZeroTotalPaymentAmountForSuccessfulProject() {
        assertOrderContractViolation(
                () -> List.of(new ProjectOutcome(308L, 408L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(new ProjectOrders(
                        308L,
                        List.of(
                                new OrderPayment(3_006L, Money.wons(0)),
                                new OrderPayment(3_007L, Money.wons(0))
                        )
                ))
        );
    }

    @Test
    @DisplayName("Order 계약 오류가 있으면 정상 프로젝트 정산과 결제 취소를 일부 실행하지 않는다")
    void preventsPartialFinancialProcessingWhenOrderContractIsInvalid() {
        long successfulProjectId = 309L;
        long failedProjectId = 310L;
        creatorPayoutProfileRepository.save(
                payoutReadyProfile(409L, "seller-409", "********0409")
        );
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(
                        new ProjectOutcome(successfulProjectId, 409L, ProjectOutcomeStatus.SUCCEEDED),
                        new ProjectOutcome(failedProjectId, 410L, ProjectOutcomeStatus.FAILED),
                        new ProjectOutcome(311L, 411L, ProjectOutcomeStatus.SUCCEEDED)
                ),
                projectIds -> List.of(
                        projectOrders(successfulProjectId, 3_008L),
                        projectOrders(failedProjectId, 3_009L),
                        projectOrders(311L)
                ),
                requests -> {
                    throw new AssertionError("계약 검증 전에 결제 취소를 요청하면 안 됩니다.");
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        assertThatThrownBy(() -> runService.run(command()))
                .isInstanceOfSatisfying(
                        SettlementException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ORDER_PAYMENT_INPUTS_UNAVAILABLE)
                );
        assertThat(projectSettlementService.findConfirmedByProjectId(successfulProjectId)).isEmpty();
        assertThat(cancellationCommandService.findAllByProjectIdIn(Set.of(failedProjectId)))
                .isEmpty();
    }

    @Test
    @DisplayName("신규 Order 계약 오류는 앞서 확정된 프로젝트 정산을 롤백하지 않는다")
    void preservesExistingSettlementWhenNewOrderContractIsInvalid() {
        long existingProjectId = 312L;
        long existingCreatorId = 412L;
        creatorPayoutProfileRepository.save(payoutReadyProfile(
                existingCreatorId,
                "seller-412",
                "********0412"
        ));
        ProjectSettlementRunService initialRunService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(
                        existingProjectId,
                        existingCreatorId,
                        ProjectOutcomeStatus.SUCCEEDED
                )),
                projectIds -> List.of(projectOrders(existingProjectId, 3_010L)),
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );
        initialRunService.run(command());
        ProjectSettlementRunService invalidRunService = new ProjectSettlementRunService(
                () -> List.of(
                        new ProjectOutcome(
                                existingProjectId,
                                existingCreatorId,
                                ProjectOutcomeStatus.SUCCEEDED
                        ),
                        new ProjectOutcome(313L, 413L, ProjectOutcomeStatus.SUCCEEDED)
                ),
                projectIds -> List.of(projectOrders(313L)),
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        assertThatThrownBy(() -> invalidRunService.run(command()))
                .isInstanceOfSatisfying(
                        SettlementException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ORDER_PAYMENT_INPUTS_UNAVAILABLE)
                );
        assertThat(projectSettlementService.findConfirmedByProjectId(existingProjectId))
                .isPresent();
    }

    @Test
    @DisplayName("성공 주문 금액은 정산하고 실패·취소 주문 식별자는 결제 취소에 사용한다")
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
                    projectOrders(111L, 1_001L),
                    projectOrders(112L, 1_002L),
                    projectOrders(113L, 1_003L)
            );
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
                cancellationGateway,
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(requestedProjectIds.get()).containsExactlyInAnyOrder(111L, 112L, 113L);
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

    @ParameterizedTest
    @EnumSource(
            value = ProjectOutcomeStatus.class,
            names = {"FAILED", "CANCELLED"}
    )
    @DisplayName("주문이 없는 실패·취소 프로젝트는 Payment를 호출하지 않고 완료로 처리한다")
    void completesUnsuccessfulProjectWithoutOrdersAsNoOp(ProjectOutcomeStatus outcomeStatus) {
        long projectId = outcomeStatus == ProjectOutcomeStatus.FAILED ? 117L : 118L;
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 217L, outcomeStatus)),
                projectIds -> List.of(projectOrders(projectId)),
                requests -> {
                    throw new AssertionError("빈 결제 취소 목록을 호출하면 안 됩니다.");
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(tuple(
                        projectId,
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
        long projectId = 130L + paymentStatus.ordinal();
        long orderId = 1_100L + paymentStatus.ordinal();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 218L, ProjectOutcomeStatus.FAILED)),
                projectIds -> List.of(projectOrders(projectId, orderId)),
                requests -> List.of(new ProjectPaymentCancellationResult(orderId, paymentStatus)),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult result = runService.run(command());

        assertThat(result.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(expectedStatus);
    }

    @Test
    @DisplayName("완료한 결제 취소 명령은 재실행 시 Order와 Payment 호출 없이 복원한다")
    void restoresCompletedCancellationCommandWithoutExternalCalls() {
        List<String> observedIdempotencyKeys = new ArrayList<>();
        AtomicInteger orderReads = new AtomicInteger();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(119L, 219L, ProjectOutcomeStatus.CANCELLED)),
                projectIds -> {
                    if (orderReads.getAndIncrement() > 0) {
                        throw new AssertionError("완료한 결제 취소 명령의 Order를 다시 조회하면 안 됩니다.");
                    }
                    return List.of(projectOrders(119L, 1_008L));
                },
                requests -> {
                    observedIdempotencyKeys.add(requests.getFirst().idempotencyKey());
                    return List.of(new ProjectPaymentCancellationResult(1_008L, COMPLETED));
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult first = runService.run(command());
        ProjectSettlementRunResult second = runService.run(command());

        assertThat(orderReads).hasValue(1);
        assertThat(observedIdempotencyKeys)
                .singleElement()
                .satisfies(idempotencyKey -> assertThat(idempotencyKey).isNotBlank());
        assertThat(first.projectResults()).containsExactlyElementsOf(second.projectResults());
    }

    @Test
    @DisplayName("결제 취소 명령의 원본을 Payment 호출 전에 저장하고 응답 결과를 반영한다")
    void persistsCancellationCommandBeforeGatewayAndRecordsResult() {
        long projectId = 140L;
        long orderId = 1_140L;
        AtomicReference<String> observedIdempotencyKey = new AtomicReference<>();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 240L, ProjectOutcomeStatus.FAILED)),
                projectIds -> List.of(projectOrders(projectId, orderId)),
                requests -> {
                    ProjectPaymentCancellationCommand stored = cancellationCommandService
                            .findAllByProjectIdIn(Set.of(projectId))
                            .getFirst();
                    assertThat(stored.projectId()).isEqualTo(projectId);
                    assertThat(stored.orderId()).isEqualTo(orderId);
                    assertThat(stored.reason()).isEqualTo(PROJECT_FAILED);
                    assertThat(stored.status())
                            .isEqualTo(ProjectPaymentCancellationCommandStatus.REQUESTED);
                    assertThat(stored.idempotencyKey()).isNotBlank();
                    observedIdempotencyKey.set(requests.getFirst().idempotencyKey());
                    assertThat(stored.idempotencyKey()).isEqualTo(observedIdempotencyKey.get());
                    return List.of(new ProjectPaymentCancellationResult(orderId, COMPLETED));
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        runService.run(command());

        assertThat(cancellationCommandService.findAllByProjectIdIn(Set.of(projectId)))
                .singleElement()
                .satisfies(command -> {
                    assertThat(command.idempotencyKey()).isEqualTo(observedIdempotencyKey.get());
                    assertThat(command.status())
                            .isEqualTo(ProjectPaymentCancellationCommandStatus.COMPLETED);
                });
    }

    @Test
    @DisplayName("부분 처리 중 결과는 완료한 주문을 건너뛰고 같은 멱등키로 재개한다")
    void resumesOnlyUnfinishedCancellationCommandsWithSameIdempotencyKey() {
        long projectId = 141L;
        long completedOrderId = 1_141L;
        long processingOrderId = 1_142L;
        AtomicInteger orderReads = new AtomicInteger();
        AtomicInteger gatewayCalls = new AtomicInteger();
        AtomicReference<String> processingIdempotencyKey = new AtomicReference<>();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 241L, ProjectOutcomeStatus.CANCELLED)),
                projectIds -> {
                    orderReads.incrementAndGet();
                    return List.of(projectOrders(projectId, completedOrderId, processingOrderId));
                },
                requests -> {
                    if (gatewayCalls.getAndIncrement() == 0) {
                        processingIdempotencyKey.set(requests.stream()
                                .filter(request -> request.orderId().equals(processingOrderId))
                                .findFirst()
                                .orElseThrow()
                                .idempotencyKey());
                        return List.of(
                                new ProjectPaymentCancellationResult(completedOrderId, COMPLETED),
                                new ProjectPaymentCancellationResult(processingOrderId, PROCESSING)
                        );
                    }
                    assertThat(requests)
                            .extracting(
                                    ProjectPaymentCancellationRequest::orderId,
                                    ProjectPaymentCancellationRequest::idempotencyKey
                            )
                            .containsExactly(tuple(
                                    processingOrderId,
                                    processingIdempotencyKey.get()
                            ));
                    return List.of(new ProjectPaymentCancellationResult(
                            processingOrderId,
                            COMPLETED
                    ));
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult first = runService.run(command());
        ProjectSettlementRunResult second = runService.run(command());

        assertThat(orderReads).hasValue(1);
        assertThat(gatewayCalls).hasValue(2);
        assertThat(first.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_PROCESSING);
        assertThat(second.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED);
    }

    @Test
    @DisplayName("결과를 확정할 수 없는 호출은 UNKNOWN으로 남기고 같은 멱등키로 재개한다")
    void resumesUnknownCancellationAfterGatewayFailure() {
        long projectId = 142L;
        long orderId = 1_143L;
        AtomicInteger orderReads = new AtomicInteger();
        AtomicInteger gatewayCalls = new AtomicInteger();
        List<String> observedIdempotencyKeys = new ArrayList<>();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 242L, ProjectOutcomeStatus.FAILED)),
                projectIds -> {
                    orderReads.incrementAndGet();
                    return List.of(projectOrders(projectId, orderId));
                },
                requests -> {
                    observedIdempotencyKeys.add(requests.getFirst().idempotencyKey());
                    if (gatewayCalls.getAndIncrement() == 0) {
                        throw new IllegalStateException("Payment 응답을 확인할 수 없습니다.");
                    }
                    return List.of(new ProjectPaymentCancellationResult(orderId, COMPLETED));
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        assertThatThrownBy(() -> runService.run(command()))
                .isInstanceOfSatisfying(
                        SettlementException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(PROJECT_PAYMENT_CANCELLATION_UNAVAILABLE)
                );
        assertThat(cancellationCommandService.findAllByProjectIdIn(Set.of(projectId)))
                .extracting(ProjectPaymentCancellationCommand::status)
                .containsExactly(ProjectPaymentCancellationCommandStatus.UNKNOWN);

        ProjectSettlementRunResult recovered = runService.run(command());

        assertThat(orderReads).hasValue(1);
        assertThat(gatewayCalls).hasValue(2);
        assertThat(observedIdempotencyKeys).hasSize(2);
        assertThat(observedIdempotencyKeys.getLast())
                .isEqualTo(observedIdempotencyKeys.getFirst());
        assertThat(recovered.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_COMPLETED);
    }

    @Test
    @DisplayName("최종 실패한 결제 취소 명령은 자동 재호출하지 않는다")
    void doesNotAutomaticallyRetryFinalFailedCancellation() {
        long projectId = 143L;
        long orderId = 1_144L;
        AtomicInteger orderReads = new AtomicInteger();
        AtomicInteger gatewayCalls = new AtomicInteger();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 243L, ProjectOutcomeStatus.FAILED)),
                projectIds -> {
                    orderReads.incrementAndGet();
                    return List.of(projectOrders(projectId, orderId));
                },
                requests -> {
                    gatewayCalls.incrementAndGet();
                    return List.of(new ProjectPaymentCancellationResult(
                            orderId,
                            ProjectPaymentCancellationStatus.FINAL_FAILED
                    ));
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult first = runService.run(command());
        ProjectSettlementRunResult second = runService.run(command());

        assertThat(orderReads).hasValue(1);
        assertThat(gatewayCalls).hasValue(1);
        assertThat(first.projectResults()).containsExactlyElementsOf(second.projectResults());
        assertThat(second.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.PAYMENT_CANCELLATION_FINAL_FAILED);
    }

    @Test
    @DisplayName("결제 취소가 시작된 프로젝트가 성공으로 관찰되면 외부 호출 없이 충돌로 남긴다")
    void rejectsSucceededOutcomeAfterCancellationStarted() {
        long projectId = 144L;
        long orderId = 1_145L;
        AtomicReference<ProjectOutcomeStatus> observedStatus =
                new AtomicReference<>(ProjectOutcomeStatus.CANCELLED);
        AtomicInteger orderReads = new AtomicInteger();
        AtomicInteger gatewayCalls = new AtomicInteger();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, 244L, observedStatus.get())),
                projectIds -> {
                    orderReads.incrementAndGet();
                    return List.of(projectOrders(projectId, orderId));
                },
                requests -> {
                    gatewayCalls.incrementAndGet();
                    return List.of(new ProjectPaymentCancellationResult(orderId, COMPLETED));
                },
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        runService.run(command());
        observedStatus.set(ProjectOutcomeStatus.SUCCEEDED);
        ProjectSettlementRunResult conflicted = runService.run(command());

        assertThat(orderReads).hasValue(1);
        assertThat(gatewayCalls).hasValue(1);
        assertThat(conflicted.confirmedSettlements()).isEmpty();
        assertThat(conflicted.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(tuple(
                        projectId,
                        ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT
                ));
    }

    @Test
    @DisplayName("주문 일부의 결제 취소 결과가 불명확하면 프로젝트를 완료로 추정하지 않는다")
    void preservesUnknownPartialCancellationResult() {
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(120L, 220L, ProjectOutcomeStatus.FAILED)),
                projectIds -> List.of(projectOrders(120L, 1_009L, 1_010L)),
                requests -> List.of(
                        new ProjectPaymentCancellationResult(1_009L, COMPLETED),
                        new ProjectPaymentCancellationResult(
                                1_010L,
                                ProjectPaymentCancellationStatus.UNKNOWN
                        )
                ),
                projectSettlementService,
                cancellationCommandService,
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
        ProjectOrderReader orderReader = projectIds -> projectIds.stream()
                .map(projectId -> new ProjectOrders(
                        projectId,
                        List.of(new OrderPayment(projectId, amountsByOrder.get(projectId)))
                ))
                .toList();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
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
    @DisplayName("이미 확정된 성공 프로젝트는 Order 재조회 없이 기존 정산을 복원한다")
    void restoresExistingSettlementBeforeExternalInputReads() {
        long projectId = 103L;
        long creatorId = 203L;
        creatorPayoutProfileRepository.save(
                payoutReadyProfile(creatorId, "seller-203", "********0203")
        );
        ProjectOutcomeReader outcomeReader = () -> List.of(
                new ProjectOutcome(projectId, creatorId, ProjectOutcomeStatus.SUCCEEDED)
        );
        AtomicInteger orderReads = new AtomicInteger();
        ProjectOrderReader orderReader = projectIds -> {
            if (orderReads.getAndIncrement() > 0) {
                throw new AssertionError("기존 프로젝트 정산의 Order를 다시 조회하면 안 됩니다.");
            }
            return List.of(projectOrders(projectId, projectId));
        };
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
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

        assertThat(orderReads).hasValue(1);
        assertThat(first.confirmedSettlements())
                .containsExactlyElementsOf(second.confirmedSettlements());
        assertThat(second.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::creatorPayoutAmount)
                .containsExactly(Money.wons(91_200));
        assertThat(first.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.SETTLEMENT_CONFIRMED);
        assertThat(second.projectResults())
                .extracting(ProjectOutcomeProcessingResult::processingStatus)
                .containsExactly(ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("기존 정산과 신규 성공 프로젝트가 섞이면 신규 프로젝트만 외부 조회한다")
    void readsExternalInputsOnlyForNewProjectAmongExistingSettlements() {
        creatorPayoutProfileRepository.save(payoutReadyProfile(221L, "seller-221", "********0221"));
        creatorPayoutProfileRepository.save(payoutReadyProfile(222L, "seller-222", "********0222"));
        ProjectSettlementRunService initialRunService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(121L, 221L, ProjectOutcomeStatus.SUCCEEDED)),
                projectIds -> List.of(projectOrders(121L, 1_011L)),
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );
        initialRunService.run(command());
        AtomicReference<Set<Long>> requestedProjectIds = new AtomicReference<>();
        ProjectSettlementRunService mixedRunService = new ProjectSettlementRunService(
                () -> List.of(
                        new ProjectOutcome(121L, 221L, ProjectOutcomeStatus.SUCCEEDED),
                        new ProjectOutcome(122L, 222L, ProjectOutcomeStatus.SUCCEEDED)
                ),
                projectIds -> {
                    requestedProjectIds.set(projectIds);
                    return List.of(new ProjectOrders(
                            122L,
                            List.of(new OrderPayment(1_012L, Money.wons(200_000)))
                    ));
                },
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult result = mixedRunService.run(command());

        assertThat(requestedProjectIds.get()).containsExactly(122L);
        assertThat(result.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(
                        tuple(121L, ProjectOutcomeProcessingStatus.SETTLEMENT_ALREADY_CONFIRMED),
                        tuple(122L, ProjectOutcomeProcessingStatus.SETTLEMENT_CONFIRMED)
                );
        assertThat(result.confirmedSettlements())
                .extracting(ConfirmedProjectSettlement::projectId)
                .containsExactly(121L, 122L);
    }

    @ParameterizedTest
    @EnumSource(
            value = ProjectOutcomeStatus.class,
            names = {"FAILED", "CANCELLED"}
    )
    @DisplayName("이미 확정된 프로젝트가 실패·취소로 관찰되면 기존 정산을 유지하고 충돌로 처리한다")
    void rejectsOutcomeTransitionAfterSettlementConfirmation(ProjectOutcomeStatus changedStatus) {
        long projectId = changedStatus == ProjectOutcomeStatus.FAILED ? 123L : 124L;
        long creatorId = changedStatus == ProjectOutcomeStatus.FAILED ? 223L : 224L;
        creatorPayoutProfileRepository.save(payoutReadyProfile(
                creatorId,
                "seller-" + creatorId,
                "********0" + creatorId
        ));
        AtomicReference<ProjectOutcomeStatus> observedStatus =
                new AtomicReference<>(ProjectOutcomeStatus.SUCCEEDED);
        AtomicInteger orderReads = new AtomicInteger();
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                () -> List.of(new ProjectOutcome(projectId, creatorId, observedStatus.get())),
                projectIds -> {
                    if (orderReads.getAndIncrement() > 0) {
                        throw new AssertionError("결과 충돌 프로젝트의 Order를 다시 조회하면 안 됩니다.");
                    }
                    return List.of(projectOrders(projectId, projectId));
                },
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        ProjectSettlementRunResult confirmed = runService.run(command());
        observedStatus.set(changedStatus);
        ProjectSettlementRunResult conflicted = runService.run(command());

        assertThat(orderReads).hasValue(1);
        assertThat(conflicted.projectResults())
                .extracting(
                        ProjectOutcomeProcessingResult::projectId,
                        ProjectOutcomeProcessingResult::outcomeStatus,
                        ProjectOutcomeProcessingResult::processingStatus
                )
                .containsExactly(tuple(
                        projectId,
                        changedStatus,
                        ProjectOutcomeProcessingStatus.OUTCOME_CONFLICT
                ));
        assertThat(conflicted.confirmedSettlements())
                .containsExactlyElementsOf(confirmed.confirmedSettlements());
    }

    private static ProjectOrders projectOrders(Long projectId, Long... orderIds) {
        return new ProjectOrders(
                projectId,
                Stream.of(orderIds)
                        .map(orderId -> new OrderPayment(orderId, Money.wons(100_000)))
                        .toList()
        );
    }

    private void assertOrderContractViolation(
            ProjectOutcomeReader outcomeReader,
            ProjectOrderReader orderReader
    ) {
        ProjectSettlementRunService runService = new ProjectSettlementRunService(
                outcomeReader,
                orderReader,
                noCancellationExpected(),
                projectSettlementService,
                cancellationCommandService,
                fixedClock()
        );

        assertThatThrownBy(() -> runService.run(command()))
                .isInstanceOfSatisfying(
                        SettlementException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ORDER_PAYMENT_INPUTS_UNAVAILABLE)
                );
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
