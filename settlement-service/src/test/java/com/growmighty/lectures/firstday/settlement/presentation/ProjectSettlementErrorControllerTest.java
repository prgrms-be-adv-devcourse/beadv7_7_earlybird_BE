package com.growmighty.lectures.firstday.settlement.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.growmighty.lectures.firstday.settlement.application.port.FinalEffectivePaymentAmountReader;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementCalculationPolicy;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "settlement.external-data.mode=error-test")
@AutoConfigureMockMvc
class ProjectSettlementErrorControllerTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

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
                .andExpect(jsonPath("$.error.code").value("S001"))
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
                .andExpect(jsonPath("$.error.code").value("S001"))
                .andExpect(jsonPath("$.error.message").value("창작자 지급 준비가 완료되지 않았습니다."));
    }

    @Test
    @DisplayName("Payment가 완전한 최종 유효 결제 금액을 제공하지 못하면 재시도 가능한 오류로 응답한다")
    void rejectsSettlementWhenFinalEffectivePaymentAmountsAreUnavailable() throws Exception {
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
                .andExpect(jsonPath("$.error.code").value("S002"))
                .andExpect(jsonPath("$.error.message").value("최종 유효 결제 금액을 확인할 수 없습니다."));
    }

    @Test
    @DisplayName("Payment Adapter 오류는 내부 메시지 없이 재시도 가능한 오류로 응답한다")
    void hidesPaymentAdapterFailureDetails() throws Exception {
        externalDataAdapter.failPaymentReadWith(
                94L,
                94L,
                new IllegalArgumentException("payment adapter secret")
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
                .andExpect(jsonPath("$.error.code").value("S002"))
                .andExpect(jsonPath("$.error.message").value("최종 유효 결제 금액을 확인할 수 없습니다."))
                .andExpect(content().string(not(containsString("payment adapter secret"))));
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
                .andExpect(jsonPath("$.error.code").value("S003"))
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 대상 정보를 확인할 수 없습니다."))
                .andExpect(content().string(not(containsString("project adapter secret"))));
    }

    @Test
    @DisplayName("프로젝트 정산과 지급 의무가 불일치하면 내부 정합성 오류로 응답한다")
    void rejectsSettlementWhenPayoutObligationIsMissing() throws Exception {
        long projectId = 93L;
        long creatorId = 93L;
        CreatorPayoutProfile payoutProfile = payoutReadyProfile(creatorId);
        creatorPayoutProfileRepository.save(payoutProfile);
        projectSettlementRepository.save(ProjectSettlement.confirm(
                projectId,
                creatorId,
                SettlementCalculationPolicy.current().calculate(List.of(Money.wons(100_000))),
                payoutProfile.snapshotDestination(),
                LocalDateTime.of(2026, 7, 23, 10, 0)
        ));
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
                .andExpect(jsonPath("$.error.code").value("S500"))
                .andExpect(jsonPath("$.error.message").value("프로젝트 정산 데이터가 일치하지 않습니다."))
                .andExpect(content().string(not(containsString("지급 의무가 존재하지 않습니다"))));
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
                            base_amount,
                            agency_fee_amount,
                            agency_fee_vat_amount,
                            platform_fee_amount,
                            platform_fee_vat_amount,
                            other_deduction_amount,
                            creator_payout_amount,
                            destination_creator_id,
                            destination_toss_seller_id,
                            destination_bank_code,
                            destination_masked_account_number,
                            confirmed_at,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                projectId,
                creatorId,
                100_000,
                4_000,
                400,
                4_000,
                400,
                0,
                99_999,
                creatorId,
                "seller-95",
                "088",
                "********0095",
                recordedAt,
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
                .andExpect(jsonPath("$.error.code").value("S500"))
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
            implements ProjectSettlementTargetReader, FinalEffectivePaymentAmountReader {

        private ProjectSettlementTarget target;
        private List<Money> paymentAmounts;
        private RuntimeException paymentReadFailure;
        private RuntimeException targetReadFailure;

        void respondWith(Long projectId, Long creatorId, List<Money> paymentAmounts) {
            this.target = new ProjectSettlementTarget(projectId, creatorId);
            this.paymentAmounts = List.copyOf(paymentAmounts);
            this.paymentReadFailure = null;
            this.targetReadFailure = null;
        }

        void failPaymentReadWith(Long projectId, Long creatorId, RuntimeException failure) {
            this.target = new ProjectSettlementTarget(projectId, creatorId);
            this.paymentAmounts = List.of();
            this.paymentReadFailure = failure;
            this.targetReadFailure = null;
        }

        void failTargetReadWith(RuntimeException failure) {
            this.target = null;
            this.paymentAmounts = List.of();
            this.paymentReadFailure = null;
            this.targetReadFailure = failure;
        }

        @Override
        public List<ProjectSettlementTarget> findSettlementTargets(YearMonth settlementMonth) {
            if (targetReadFailure != null) {
                throw targetReadFailure;
            }
            return List.of(target);
        }

        @Override
        public List<Money> findFinalEffectivePaymentAmounts(Long projectId) {
            if (paymentReadFailure != null) {
                throw paymentReadFailure;
            }
            return paymentAmounts;
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
