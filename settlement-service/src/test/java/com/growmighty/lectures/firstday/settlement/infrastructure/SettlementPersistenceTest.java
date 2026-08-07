// TODO(settlement-plan): Add Inbox, facts, run, reconciliation, Outbox, pgOrderId, and payout idempotency constraint coverage.
package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.infrastructure.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.model.SettlementFeePolicySnapshot;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.CreatorPayoutProfileRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.PayoutObligationRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.ProjectSettlementRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({
        JpaAuditingConfig.class,
        CreatorPayoutProfileRepositoryAdapter.class,
        PayoutObligationRepositoryAdapter.class,
        ProjectSettlementRepositoryAdapter.class
})
class SettlementPersistenceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private PayoutObligationRepository payoutObligationRepository;

    @Autowired
    private CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Autowired
    private SpringDataProjectSettlementRepository springDataProjectSettlementRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Settlement JPA Auditing이 common 감사 필드를 기록한다")
    void recordsAuditTimestampsWithCommonJpaAuditing() {
        projectSettlementRepository.save(ProjectSettlement.confirm(
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
                LocalDateTime.of(2026, 7, 22, 10, 0)
        ));
        entityManager.flush();
        entityManager.clear();

        var persisted = springDataProjectSettlementRepository.findByProjectId(1L).orElseThrow();

        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("프로젝트 정산의 금액과 확정 시점 원본을 저장하고 다시 읽는다")
    void persistsAndRestoresProjectSettlementMoney() {
        ProjectSettlement settlement = ProjectSettlement.confirm(
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
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        ProjectSettlement saved = projectSettlementRepository.save(settlement);
        entityManager.flush();
        entityManager.clear();

        ProjectSettlement restored = projectSettlementRepository.findByProjectId(1L).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(restored.creatorPayoutAmount()).isEqualTo(Money.wons(91_200));
        assertThat(restored.feePolicySnapshot()).isEqualTo(SettlementFeePolicySnapshot.current());
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
        PayoutObligation saved = payoutObligationRepository.save(obligation);
        entityManager.flush();
        entityManager.clear();

        PayoutObligation restored = payoutObligationRepository.findBySettlementId(100L).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(saved.attempts().getFirst().id()).isNotNull();
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

        CreatorPayoutProfile restored = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();

        assertThat(restored.canReceivePayout()).isTrue();
    }

    @Test
    @DisplayName("외부 셀러가 없는 등록 대기 창작자도 저장한다")
    void persistsCreatorBeforeExternalSellerRegistration() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.awaitingRegistration(10L));
        entityManager.flush();
        entityManager.clear();

        CreatorPayoutProfile restored = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();

        assertThat(restored.canReceivePayout()).isFalse();
    }

    @Test
    @DisplayName("기존 창작자 지급 프로필을 같은 JPA 엔티티에서 갱신한다")
    void updatesManagedCreatorPayoutProfile() {
        CreatorPayoutProfile saved = creatorPayoutProfileRepository.save(
                CreatorPayoutProfile.awaitingRegistration(10L)
        );
        entityManager.clear();

        CreatorPayoutProfile profile = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();
        profile.completeRegistration(
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        CreatorPayoutProfile updated = creatorPayoutProfileRepository.save(profile);
        entityManager.clear();

        CreatorPayoutProfile restored = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();

        assertThat(updated.version()).isGreaterThan(saved.version());
        assertThat(restored.canReceivePayout()).isTrue();
        assertThat(restored.tossSellerId()).isEqualTo("seller-10");
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
    @DisplayName("저장된 지급 시도를 보존하면서 결과와 재시도를 갱신한다")
    void updatesManagedPayoutObligationGraph() {
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
        PayoutObligation saved = payoutObligationRepository.save(obligation);
        Long firstAttemptId = saved.attempts().getFirst().id();
        entityManager.clear();

        PayoutObligation failed = payoutObligationRepository.findBySettlementId(100L).orElseThrow();
        failed.failAttempt(
                failed.attempts().getFirst(),
                "toss-payout-1",
                "TEMPORARY_ERROR",
                LocalDateTime.of(2026, 8, 3, 9, 1),
                true
        );
        PayoutObligation retryWaiting = payoutObligationRepository.save(failed);

        var retry = retryWaiting.startAttempt(
                "ref-payout-100-2",
                "idempotency-100-2",
                LocalDateTime.of(2026, 8, 3, 9, 2)
        );
        retryWaiting.completeAttempt(
                retry,
                "toss-payout-2",
                LocalDateTime.of(2026, 8, 3, 9, 3)
        );
        payoutObligationRepository.save(retryWaiting);
        entityManager.clear();

        PayoutObligation restored = payoutObligationRepository.findBySettlementId(100L).orElseThrow();

        assertThat(restored.status()).isEqualTo(PayoutObligationStatus.COMPLETED);
        assertThat(restored.attempts()).hasSize(2);
        assertThat(restored.attempts().getFirst().id()).isEqualTo(firstAttemptId);
        assertThat(restored.attempts().getFirst().status()).isEqualTo(PayoutAttemptStatus.FAILED);
        assertThat(restored.attempts().get(1).id()).isNotNull();
        assertThat(restored.successfulAttemptSequence()).isEqualTo(2);
    }

    @Test
    @DisplayName("오래된 지급 의무 스냅샷의 저장을 거부한다")
    void rejectsStalePayoutObligationVersion() {
        PayoutObligation saved = payoutObligationRepository.save(PayoutObligation.schedule(
                100L,
                10L,
                Money.wons(91_200),
                LocalDate.of(2026, 8, 3)
        ));
        entityManager.clear();

        PayoutObligation first = payoutObligationRepository.findBySettlementId(100L).orElseThrow();
        PayoutObligation stale = payoutObligationRepository.findBySettlementId(100L).orElseThrow();

        first.startAttempt(
                "ref-payout-100-1",
                "idempotency-100-1",
                LocalDateTime.of(2026, 8, 3, 9, 0)
        );
        PayoutObligation updated = payoutObligationRepository.save(first);

        stale.startAttempt(
                "ref-payout-100-stale",
                "idempotency-100-stale",
                LocalDateTime.of(2026, 8, 3, 9, 1)
        );

        assertThat(updated.version()).isGreaterThan(saved.version());
        assertThatThrownBy(() -> payoutObligationRepository.save(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
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
                SettlementFeePolicySnapshot.current(),
                breakdown,
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        ProjectSettlement duplicate = ProjectSettlement.confirm(
                1L,
                10L,
                SettlementFeePolicySnapshot.current(),
                breakdown,
                PayoutDestinationSnapshot.of(10L, "seller-10", "088", "********1234"),
                LocalDateTime.of(2026, 7, 22, 10, 1)
        );
        projectSettlementRepository.save(first);

        assertThatThrownBy(() -> projectSettlementRepository.save(duplicate))
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
        payoutObligationRepository.save(first);

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

        assertThatThrownBy(() -> payoutObligationRepository.save(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
