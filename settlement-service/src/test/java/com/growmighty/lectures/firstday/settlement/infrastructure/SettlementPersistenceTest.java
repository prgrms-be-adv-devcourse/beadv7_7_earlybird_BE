package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PgReconciliationRun;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPayoutRun;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.PgReconciliationRunRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPayoutRunRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.PayoutObligationRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.PgReconciliationRunRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.ProjectPayoutRunRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.ProjectSettlementRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({
        JpaAuditingConfig.class,
        ProjectSettlementRepositoryAdapter.class,
        PayoutObligationRepositoryAdapter.class,
        PgReconciliationRunRepositoryAdapter.class,
        ProjectPayoutRunRepositoryAdapter.class
})
class SettlementPersistenceTest extends MySqlIntegrationTestSupport {

    @Autowired private ProjectSettlementRepository projectSettlementRepository;
    @Autowired private PayoutObligationRepository payoutObligationRepository;
    @Autowired private PgReconciliationRunRepository pgReconciliationRunRepository;
    @Autowired private ProjectPayoutRunRepository projectPayoutRunRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("불변 프로젝트 정산과 별도 지급 의무를 저장하고 다시 읽는다")
    void persistsSeparateSettlementAndPayoutObligation() {
        ProjectSettlement settlement = projectSettlementRepository.save(settlement(1L, 10L));
        PayoutObligation payoutObligation = payoutObligationRepository.save(obligation(settlement, 10L));
        entityManager.flush();
        entityManager.clear();

        ProjectSettlement restoredSettlement = projectSettlementRepository.findById(settlement.id()).orElseThrow();
        PayoutObligation restoredObligation = payoutObligationRepository.findBySettlementId(settlement.id()).orElseThrow();

        assertThat(restoredSettlement.creatorPayoutAmount()).isEqualTo(Money.wons(91_200));
        assertThat(restoredObligation.creatorId()).isEqualTo(restoredSettlement.creatorId());
        assertThat(restoredObligation.payoutAmount()).isEqualTo(restoredSettlement.creatorPayoutAmount());
        assertThat(restoredObligation.status()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(payoutObligation.id()).isNotNull();
    }

    @Test
    @DisplayName("지급 시도는 지급 의무에 저장되고 재시도 이력을 유지한다")
    void persistsPayoutAttemptsUnderPayoutObligation() {
        ProjectSettlement settlement = projectSettlementRepository.save(settlement(1L, 10L));
        PayoutObligation payoutObligation = payoutObligationRepository.save(obligation(settlement, 10L));
        payoutObligation.startAttempt("ref-1", "key-1", LocalDateTime.of(2026, 8, 3, 9, 0));
        payoutObligationRepository.save(payoutObligation);
        entityManager.flush();
        entityManager.clear();

        PayoutObligation restored = payoutObligationRepository.findBySettlementId(settlement.id()).orElseThrow();

        assertThat(restored.attempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.refPayoutId()).isEqualTo("ref-1");
            assertThat(attempt.amount()).isEqualTo(Money.wons(91_200));
        });
    }

    @Test
    @DisplayName("하나의 정산에는 하나의 지급 의무만 저장할 수 있다")
    void rejectsDuplicatePayoutObligationForSettlement() {
        ProjectSettlement settlement = projectSettlementRepository.save(settlement(1L, 10L));
        payoutObligationRepository.save(obligation(settlement, 10L));

        assertThatThrownBy(() -> payoutObligationRepository.save(obligation(settlement, 10L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("월별 대사와 지급 실행을 각각 실행 중인 대상 월로 다시 읽는다")
    void persistsRunningMonthlyRuns() {
        YearMonth month = YearMonth.of(2026, 7);
        pgReconciliationRunRepository.save(PgReconciliationRun.start(month, LocalDateTime.of(2026, 8, 3, 9, 0)));
        projectPayoutRunRepository.save(ProjectPayoutRun.start(month, LocalDateTime.of(2026, 8, 5, 9, 0)));
        entityManager.clear();

        assertThat(pgReconciliationRunRepository.findRunningBySettlementMonth(month))
                .get()
                .extracting(PgReconciliationRun::status)
                .isEqualTo(PgReconciliationRun.Status.RUNNING);
        assertThat(projectPayoutRunRepository.findRunningByPayoutMonth(month))
                .get()
                .extracting(ProjectPayoutRun::status)
                .isEqualTo(ProjectPayoutRun.Status.RUNNING);
    }

    @Test
    @DisplayName("같은 대상 월의 실행 중인 PG 대사는 하나만 저장할 수 있다")
    void rejectsDuplicateRunningPgReconciliationRunForMonth() {
        YearMonth month = YearMonth.of(2026, 7);
        pgReconciliationRunRepository.save(PgReconciliationRun.start(month, LocalDateTime.of(2026, 8, 3, 9, 0)));

        assertThatThrownBy(() -> pgReconciliationRunRepository.save(
                PgReconciliationRun.start(month, LocalDateTime.of(2026, 8, 3, 9, 1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 대상 월의 실행 중인 프로젝트 지급은 하나만 저장할 수 있다")
    void rejectsDuplicateRunningProjectPayoutRunForMonth() {
        YearMonth month = YearMonth.of(2026, 8);
        projectPayoutRunRepository.save(ProjectPayoutRun.start(month, LocalDateTime.of(2026, 8, 5, 9, 0)));

        assertThatThrownBy(() -> projectPayoutRunRepository.save(
                ProjectPayoutRun.start(month, LocalDateTime.of(2026, 8, 5, 9, 1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("종료된 월별 실행은 활성 키를 비워 같은 월 재실행을 허용한다")
    void allowsRerunningFinishedMonthlyRuns() {
        YearMonth month = YearMonth.of(2026, 7);
        PgReconciliationRun reconciliationRun = pgReconciliationRunRepository.save(
                PgReconciliationRun.start(month, LocalDateTime.of(2026, 8, 3, 9, 0))
        );
        ProjectPayoutRun payoutRun = projectPayoutRunRepository.save(
                ProjectPayoutRun.start(month, LocalDateTime.of(2026, 8, 5, 9, 0))
        );
        reconciliationRun.requireReview(LocalDateTime.of(2026, 8, 3, 9, 1));
        payoutRun.complete(LocalDateTime.of(2026, 8, 5, 9, 1));
        pgReconciliationRunRepository.save(reconciliationRun);
        projectPayoutRunRepository.save(payoutRun);

        assertThat(pgReconciliationRunRepository.findRunningBySettlementMonth(month)).isEmpty();
        assertThat(projectPayoutRunRepository.findRunningByPayoutMonth(month)).isEmpty();
        assertThat(pgReconciliationRunRepository.save(
                PgReconciliationRun.start(month, LocalDateTime.of(2026, 8, 4, 9, 0))
        ).id()).isNotNull();
        assertThat(projectPayoutRunRepository.save(
                ProjectPayoutRun.start(month, LocalDateTime.of(2026, 9, 5, 9, 0))
        ).id()).isNotNull();
    }

    private static ProjectSettlement settlement(Long projectId, Long creatorId) {
        return ProjectSettlement.confirm(projectId, creatorId, List.of(Money.wons(100_000)), LocalDateTime.of(2026, 7, 22, 10, 0));
    }

    private static PayoutObligation obligation(ProjectSettlement settlement, Long creatorId) {
        return PayoutObligation.schedule(settlement, CreatorPayoutProfile.registered(
                creatorId, "seller-" + creatorId, CreatorPayoutStatus.PAYOUT_READY, "088", "********1234",
                LocalDateTime.of(2026, 7, 22, 9, 0)), LocalDate.of(2026, 8, 3));
    }
}
