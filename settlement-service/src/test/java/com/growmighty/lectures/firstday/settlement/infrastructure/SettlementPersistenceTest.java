package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import(JpaAuditingConfig.class)
class SettlementPersistenceTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ProjectSettlementJpaRepository projectSettlementRepository;

    @Autowired
    private PayoutObligationJpaRepository payoutObligationRepository;

    @Autowired
    private CreatorPayoutProfileJpaRepository creatorPayoutProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("프로젝트 정산의 Money 값을 저장하고 다시 읽는다")
    void persistsAndRestoresProjectSettlementMoney() {
        ProjectSettlement settlement = ProjectSettlement.confirm(
                1L,
                10L,
                "2026-07",
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
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        projectSettlementRepository.save(settlement);
        entityManager.flush();
        entityManager.clear();

        ProjectSettlement restored = projectSettlementRepository.findByProjectId(1L).orElseThrow();

        assertThat(restored.creatorPayoutAmount()).isEqualTo(Money.wons(91_200));
    }

    @Test
    @DisplayName("지급 의무와 지급 시도를 함께 저장하고 다시 읽는다")
    void persistsAndRestoresPayoutObligationWithAttempt() {
        PayoutObligation obligation = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );
        obligation.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        payoutObligationRepository.save(obligation);
        entityManager.flush();
        entityManager.clear();

        PayoutObligation restored = payoutObligationRepository.findBySettlementId(100L).orElseThrow();

        assertThat(restored.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CreatorId로 창작자 지급 프로필을 저장하고 다시 읽는다")
    void persistsAndRestoresCreatorPayoutProfileByCreatorId() {
        CreatorPayoutProfile profile = CreatorPayoutProfile.registered(
                10L,
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        creatorPayoutProfileRepository.save(profile);
        entityManager.flush();
        entityManager.clear();

        CreatorPayoutProfile restored = creatorPayoutProfileRepository.findById(10L).orElseThrow();

        assertThat(restored.canReceivePayout()).isTrue();
    }

    @Test
    @DisplayName("외부 셀러가 없는 등록 대기 창작자도 저장한다")
    void persistsCreatorBeforeExternalSellerRegistration() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.awaitingRegistration(10L));
        entityManager.flush();
        entityManager.clear();

        CreatorPayoutProfile restored = creatorPayoutProfileRepository.findById(10L).orElseThrow();

        assertThat(restored.canReceivePayout()).isFalse();
    }

    @Test
    @DisplayName("성공한 지급 시도와 완료된 지급 의무를 함께 저장한다")
    void persistsCompletedObligationWithSuccessfulAttempt() {
        PayoutObligation obligation = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );
        var attempt = obligation.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        obligation.completeAttempt(
                attempt,
                "toss-payout-1",
                LocalDateTime.of(2026, 8, 3, 9, 1)
        );
        payoutObligationRepository.save(obligation);
        entityManager.flush();
        entityManager.clear();

        PayoutObligation restored = payoutObligationRepository.findBySettlementId(100L).orElseThrow();

        assertThat(restored.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("같은 프로젝트의 정산을 두 번 저장할 수 없다")
    void rejectsDuplicateSettlementForSameProject() {
        SettlementBreakdown breakdown = SettlementBreakdown.of(
                Money.wons(100_000),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(4_000),
                Money.wons(400),
                Money.wons(0),
                Money.wons(91_200)
        );
        ProjectSettlement first = ProjectSettlement.confirm(
                1L,
                10L,
                "2026-07",
                breakdown,
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        ProjectSettlement duplicate = ProjectSettlement.confirm(
                1L,
                10L,
                "2026-07",
                breakdown,
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDateTime.of(2026, 7, 22, 10, 1)
        );
        projectSettlementRepository.saveAndFlush(first);

        assertThatThrownBy(() -> projectSettlementRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 멱등키의 지급 시도를 두 번 저장할 수 없다")
    void rejectsDuplicatePayoutAttemptIdempotencyKey() {
        PayoutObligation first = PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        );
        first.startAttempt(
                "ref-payout-100-1",
                "same-idempotency-key",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        payoutObligationRepository.saveAndFlush(first);

        PayoutObligation second = PayoutObligation.schedule(
                101L,
                11L,
                Money.wons(50_000),
                LocalDate.of(2026, 8, 3)
        );
        second.startAttempt(
                "ref-payout-101-1",
                "same-idempotency-key",
                LocalDateTime.of(2026, 8, 3, 9, 1)
        );

        assertThatThrownBy(() -> payoutObligationRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
