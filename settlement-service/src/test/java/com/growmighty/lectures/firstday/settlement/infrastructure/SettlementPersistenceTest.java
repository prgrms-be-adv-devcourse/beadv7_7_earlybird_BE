package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.PayoutObligationRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.ProjectSettlementRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({JpaAuditingConfig.class, ProjectSettlementRepositoryAdapter.class, PayoutObligationRepositoryAdapter.class})
class SettlementPersistenceTest extends MySqlIntegrationTestSupport {

    @Autowired private ProjectSettlementRepository projectSettlementRepository;
    @Autowired private PayoutObligationRepository payoutObligationRepository;
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

    private static ProjectSettlement settlement(Long projectId, Long creatorId) {
        return ProjectSettlement.confirm(projectId, creatorId, List.of(Money.wons(100_000)), LocalDateTime.of(2026, 7, 22, 10, 0));
    }

    private static PayoutObligation obligation(ProjectSettlement settlement, Long creatorId) {
        return PayoutObligation.schedule(settlement, CreatorPayoutProfile.registered(
                creatorId, "seller-" + creatorId, CreatorPayoutStatus.PAYOUT_READY, "088", "********1234",
                LocalDateTime.of(2026, 7, 22, 9, 0)), LocalDate.of(2026, 8, 3));
    }
}
