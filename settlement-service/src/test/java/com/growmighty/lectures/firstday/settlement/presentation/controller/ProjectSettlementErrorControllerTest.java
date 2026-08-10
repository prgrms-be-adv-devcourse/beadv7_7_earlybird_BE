// TODO(settlement-plan): Cover validation, reconciliation review, and event-contract errors through the HTTP interface.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPayment;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrders;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcome;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeReader;
import com.growmighty.lectures.firstday.settlement.application.port.project.ProjectOutcomeStatus;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "settlement.external-data.mode=error-test",
        "settlement.project-target.mode=error-test",
        "settlement.project-order.mode=error-test",
        "settlement.payment-cancellation.mode=error-test"
})
@AutoConfigureMockMvc
class ProjectSettlementErrorControllerTest extends MySqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestExternalDataAdapter externalDataAdapter;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("지급 프로필이 준비되지 않은 프로젝트 정산은 Settlement 오류로 응답한다")
    void rejectsSettlementWhenPayoutProfileIsNotReady() throws Exception {
        externalDataAdapter.respondWith(91L, 91L, List.of(Money.wons(100_000)));

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("창작자 지급 준비가 완료되지 않았습니다."));
    }

    @Test
    @DisplayName("지급 프로필이 승인 대기 중이면 지급 준비 미완료 오류로 응답한다")
    void rejectsSettlementWhenPayoutProfileAwaitsApproval() throws Exception {
        long creatorId = 96L;
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                creatorId,
                "seller-96",
                CreatorPayoutStatus.APPROVAL_REQUIRED,
                "088",
                "********0096",
                LocalDateTime.of(2026, 7, 23, 9, 0)
        ));
        externalDataAdapter.respondWith(96L, creatorId, List.of(Money.wons(100_000)));

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("창작자 지급 준비가 완료되지 않았습니다."));
    }

    @Test
    @DisplayName("Order가 완전한 주문 결제 금액을 제공하지 못하면 재시도 가능한 오류로 응답한다")
    void rejectsSettlementWhenOrderPaymentInputsAreUnavailable() throws Exception {
        long creatorId = 92L;
        creatorPayoutProfileRepository.save(payoutReadyProfile(creatorId));
        externalDataAdapter.respondWith(92L, creatorId, List.of());

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("주문 결제금액을 확인할 수 없습니다."));
    }

    @Test
    @DisplayName("Order Adapter 오류는 내부 메시지 없이 재시도 가능한 오류로 응답한다")
    void hidesOrderAdapterFailureDetails() throws Exception {
        externalDataAdapter.failOrderReadWith(
                94L,
                94L,
                new IllegalArgumentException("order adapter secret")
        );

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("주문 결제금액을 확인할 수 없습니다."))
                .andExpect(content().string(not(containsString("order adapter secret"))));
    }

    @Test
    @DisplayName("Project Adapter 오류는 내부 메시지 없이 재시도 가능한 오류로 응답한다")
    void hidesProjectAdapterFailureDetails() throws Exception {
        externalDataAdapter.failTargetReadWith(new IllegalStateException("project adapter secret"));

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 대상 정보를 확인할 수 없습니다."))
                .andExpect(content().string(not(containsString("project adapter secret"))));
    }

    @Test
    @DisplayName("저장된 프로젝트 정산 원본의 정합성 오류는 내부 정보를 노출하지 않는다")
    void hidesPersistenceIntegrityFailureDetails() throws Exception {
        long projectId = 95L;
        long creatorId = 95L;
        LocalDateTime recordedAt = LocalDateTime.of(2026, 7, 23, 10, 0);
        jdbcTemplate.update("""
                        INSERT INTO project_settlements (
                            project_id,
                            creator_id,
                            payment_and_settlement_agency_fee_rate,
                            platform_fee_rate,
                            fee_vat_rate,
                            base_amount,
                            agency_fee_amount,
                            agency_fee_vat_amount,
                            platform_fee_amount,
                            platform_fee_vat_amount,
                            other_deduction_amount,
                            creator_payout_amount,
                            destination_toss_seller_id,
                            destination_bank_code,
                            destination_masked_account_number,
                            scheduled_date,
                            status,
                            confirmed_at,
                            version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                projectId,
                creatorId,
                0.04,
                0.04,
                0.10,
                100_000,
                4_000,
                400,
                4_000,
                400,
                0,
                99_999,
                "seller-95",
                "088",
                "********0095",
                LocalDate.of(2026, 8, 3),
                "SCHEDULED",
                recordedAt,
                0,
                recordedAt,
                recordedAt
        );
        externalDataAdapter.respondWith(projectId, creatorId, List.of(Money.wons(100_000)));

        mockMvc.perform(post("/internal/v1/settlements/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settlementMonth": "2026-07"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 데이터가 일치하지 않습니다."))
                .andExpect(content().string(not(containsString("창작자 지급액이 공제 후 금액과 일치하지 않습니다"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExternalDataTestConfig {

        @Bean
        TestExternalDataAdapter testExternalDataAdapter() {
            return new TestExternalDataAdapter();
        }
    }

    static class TestExternalDataAdapter
            implements ProjectOutcomeReader,
            ProjectOrderReader,
            ProjectPaymentCancellationGateway {

        private ProjectOutcome outcome;
        private List<OrderPayment> orders;
        private RuntimeException orderReadFailure;
        private RuntimeException targetReadFailure;

        void respondWith(Long projectId, Long creatorId, List<Money> paymentAmounts) {
            this.outcome = new ProjectOutcome(projectId, creatorId, ProjectOutcomeStatus.SUCCEEDED);
            this.orders = IntStream.range(0, paymentAmounts.size())
                    .mapToObj(index -> new OrderPayment(
                            projectId * 1_000 + index + 1,
                            paymentAmounts.get(index)
                    ))
                    .toList();
            this.orderReadFailure = null;
            this.targetReadFailure = null;
        }

        void failOrderReadWith(Long projectId, Long creatorId, RuntimeException failure) {
            this.outcome = new ProjectOutcome(projectId, creatorId, ProjectOutcomeStatus.SUCCEEDED);
            this.orders = List.of();
            this.orderReadFailure = failure;
            this.targetReadFailure = null;
        }

        void failTargetReadWith(RuntimeException failure) {
            this.outcome = null;
            this.orders = List.of();
            this.orderReadFailure = null;
            this.targetReadFailure = failure;
        }

        @Override
        public List<ProjectOutcome> findProjectOutcomes() {
            if (targetReadFailure != null) {
                throw targetReadFailure;
            }
            return List.of(outcome);
        }

        @Override
        public List<ProjectOrders> findProjectOrders(Set<Long> projectIds) {
            if (orderReadFailure != null) {
                throw orderReadFailure;
            }
            return List.of(new ProjectOrders(outcome.projectId(), orders));
        }

        @Override
        public List<ProjectPaymentCancellationResult> cancel(
                List<ProjectPaymentCancellationRequest> requests
        ) {
            return requests.stream()
                    .map(ProjectPaymentCancellationRequest::orderId)
                    .map(orderId -> new ProjectPaymentCancellationResult(
                            orderId,
                            ProjectPaymentCancellationStatus.COMPLETED
                    ))
                    .toList();
        }
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
