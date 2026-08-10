// TODO(settlement-plan): Add Inbox, facts, run, reconciliation, Outbox, pgOrderId, and payout idempotency constraint coverage.
package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.infrastructure.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutAttemptStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.repository.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutObligationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.PayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.CreatorPayoutProfileRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.PayoutObligationRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.ProjectSettlementRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
    private SpringDataProjectOutcomeFactRepository projectOutcomeFactRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository orderPaymentFactRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Settlement JPA Auditing이 common 감사 필드를 기록한다")
    void recordsAuditTimestampsWithCommonJpaAuditing() {
        projectSettlementRepository.save(confirmedSettlement(1L, 10L, LocalDateTime.of(2026, 7, 22, 10, 0)));
        entityManager.flush();
        entityManager.clear();

        var persisted = springDataProjectSettlementRepository.findByProjectId(1L).orElseThrow();

        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("프로젝트 정산의 금액과 확정 시점 원본을 저장하고 다시 읽는다")
    void persistsAndRestoresProjectSettlementMoney() {
        ProjectSettlement settlement = confirmedSettlement(1L, 10L, LocalDateTime.of(2026, 7, 22, 10, 0));
        ProjectSettlement saved = projectSettlementRepository.save(settlement);
        entityManager.flush();
        entityManager.clear();

        ProjectSettlement restored = projectSettlementRepository.findByProjectId(1L).orElseThrow();

        assertThat(saved.id()).isNotNull();
        assertThat(restored.creatorPayoutAmount()).isEqualTo(Money.wons(91_200));
        assertThat(restored.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(restored.status()).isEqualTo(PayoutStatus.SCHEDULED);
        assertThat(restored.tossSellerId()).isEqualTo("seller-10");
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
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 7, 22, 10, 0);
        CreatorPayoutProfile profile = CreatorPayoutProfile.registered(
                10L,
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                verifiedAt
        );
        creatorPayoutProfileRepository.save(profile);
        entityManager.flush();
        entityManager.clear();

        CreatorPayoutProfile restored = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();

        assertThat(restored.canReceivePayout()).isTrue();
        assertThat(restored.tossSellerId()).isEqualTo("seller-10");
        assertThat(restored.status()).isEqualTo(CreatorPayoutStatus.PAYOUT_READY);
        assertThat(restored.bankCode()).isEqualTo("088");
        assertThat(restored.maskedAccountNumber()).isEqualTo("********1234");
        assertThat(restored.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(restored.version()).isNotNull();
        assertThat(restored.getCreatedAt()).isNotNull();
        assertThat(restored.getUpdatedAt()).isNotNull();
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
    @DisplayName("오래된 창작자 지급 프로필의 저장을 거부한다")
    void rejectsStaleCreatorPayoutProfileVersion() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.awaitingRegistration(10L));
        entityManager.clear();

        CreatorPayoutProfile first = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();
        entityManager.clear();
        CreatorPayoutProfile stale = creatorPayoutProfileRepository.findByCreatorId(10L).orElseThrow();
        entityManager.clear();

        first.completeRegistration(
                "seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        );
        creatorPayoutProfileRepository.save(first);
        entityManager.clear();

        stale.completeRegistration(
                "stale-seller-10",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********5678",
                LocalDateTime.of(2026, 7, 22, 10, 1)
        );

        assertThatThrownBy(() -> creatorPayoutProfileRepository.save(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("같은 토스 셀러 식별자를 두 프로필에 저장할 수 없다")
    void rejectsDuplicateTossSellerId() {
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                10L,
                "seller-shared",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********1234",
                LocalDateTime.of(2026, 7, 22, 10, 0)
        ));

        CreatorPayoutProfile duplicate = CreatorPayoutProfile.registered(
                11L,
                "seller-shared",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********5678",
                LocalDateTime.of(2026, 7, 22, 10, 1)
        );

        assertThatThrownBy(() -> creatorPayoutProfileRepository.save(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB에서 읽은 창작자 지급 프로필의 상태 조합을 검증한다")
    void validatesCreatorPayoutProfileAfterLoad() {
        entityManager.createNativeQuery("""
                insert into creator_payout_profiles (
                    creator_id, toss_seller_id, status, version, created_at, updated_at
                ) values (
                    10, 'seller-10', 'REGISTRATION_PENDING', 0, now(), now()
                )
                """).executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> creatorPayoutProfileRepository.findByCreatorId(10L))
                .hasRootCauseMessage("셀러 등록 대기 중에는 외부 셀러와 계좌 정보를 가질 수 없습니다.");
    }

    @Test
    @DisplayName("프로젝트 결과 사실과 결과 시각을 저장한다")
    void persistsProjectOutcomeFact() {
        Instant occurredAt = Instant.parse("2026-07-31T09:00:00Z");
        projectOutcomeFactRepository.saveAndFlush(ProjectOutcomeFact.of(
                101L,
                9L,
                ProjectOutcomeFact.Outcome.SUCCEEDED,
                occurredAt
        ));
        entityManager.clear();

        ProjectOutcomeFact restored = projectOutcomeFactRepository.findById(101L).orElseThrow();

        assertThat(restored.creatorId()).isEqualTo(9L);
        assertThat(restored.outcome()).isEqualTo(ProjectOutcomeFact.Outcome.SUCCEEDED);
        assertThat(restored.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("같은 프로젝트의 결과 사실을 덮어쓸 수 없다")
    void rejectsConflictingProjectOutcome() {
        projectOutcomeFactRepository.saveAndFlush(ProjectOutcomeFact.of(
                101L,
                9L,
                ProjectOutcomeFact.Outcome.SUCCEEDED,
                Instant.parse("2026-07-31T09:00:00Z")
        ));

        ProjectOutcomeFact conflicting = ProjectOutcomeFact.of(
                101L,
                9L,
                ProjectOutcomeFact.Outcome.CANCELLED,
                Instant.parse("2026-07-31T09:01:00Z")
        );

        assertThatThrownBy(() -> projectOutcomeFactRepository.saveAndFlush(conflicting))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("프로젝트별 주문 결제 사실과 완료·취소 시각을 저장한다")
    void persistsOrderPaymentFactResults() {
        Instant completedAt = Instant.parse("2026-07-15T04:20:10Z");
        Instant cancelledAt = Instant.parse("2026-07-18T00:05:00Z");
        orderPaymentFactRepository.saveAndFlush(OrderPaymentFact.completed(
                1001L,
                "PAY-01J2X8P4QW6YV0M3",
                101L,
                Money.wons(50_000),
                completedAt
        ));
        entityManager.clear();

        OrderPaymentFact fact = orderPaymentFactRepository.findById(1001L).orElseThrow();
        fact.cancel("PAY-01J2X8P4QW6YV0M3", 101L, Money.wons(50_000), cancelledAt);
        orderPaymentFactRepository.saveAndFlush(fact);
        entityManager.clear();

        OrderPaymentFact restored = orderPaymentFactRepository
                .findAllByProjectIdOrderByOrderId(101L)
                .getFirst();

        assertThat(restored.orderId()).isEqualTo(1001L);
        assertThat(restored.pgOrderId()).isEqualTo("PAY-01J2X8P4QW6YV0M3");
        assertThat(restored.paymentAmount()).isEqualTo(Money.wons(50_000));
        assertThat(restored.status()).isEqualTo(OrderPaymentFact.Status.CANCELLED);
        assertThat(restored.completedAt()).isEqualTo(completedAt);
        assertThat(restored.cancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    @DisplayName("같은 PG 정산 식별자를 두 주문에 저장할 수 없다")
    void rejectsDuplicatePgOrderId() {
        orderPaymentFactRepository.saveAndFlush(OrderPaymentFact.completed(
                1001L,
                "PAY-SHARED",
                101L,
                Money.wons(50_000),
                Instant.parse("2026-07-15T04:20:10Z")
        ));

        OrderPaymentFact duplicate = OrderPaymentFact.completed(
                1002L,
                "PAY-SHARED",
                101L,
                Money.wons(30_000),
                Instant.parse("2026-07-15T04:21:10Z")
        );

        assertThatThrownBy(() -> orderPaymentFactRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 주문 식별자의 결제 사실을 덮어쓸 수 없다")
    void rejectsConflictingOrderPayment() {
        orderPaymentFactRepository.saveAndFlush(OrderPaymentFact.completed(
                1001L,
                "PAY-ORIGINAL",
                101L,
                Money.wons(50_000),
                Instant.parse("2026-07-15T04:20:10Z")
        ));

        OrderPaymentFact conflicting = OrderPaymentFact.completed(
                1001L,
                "PAY-CONFLICTING",
                102L,
                Money.wons(30_000),
                Instant.parse("2026-07-15T04:21:10Z")
        );

        assertThatThrownBy(() -> orderPaymentFactRepository.saveAndFlush(conflicting))
                .isInstanceOf(DataIntegrityViolationException.class);
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
        ProjectSettlement first = confirmedSettlement(1L, 10L, LocalDateTime.of(2026, 7, 22, 10, 0));
        ProjectSettlement duplicate = confirmedSettlement(1L, 10L, LocalDateTime.of(2026, 7, 22, 10, 1));
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

    private static ProjectSettlement confirmedSettlement(
            Long projectId,
            Long creatorId,
            LocalDateTime confirmedAt
    ) {
        return ProjectSettlement.confirm(
                projectId,
                creatorId,
                java.util.List.of(Money.wons(100_000)),
                CreatorPayoutProfile.registered(
                        creatorId,
                        "seller-" + creatorId,
                        CreatorPayoutStatus.PAYOUT_READY,
                        "088",
                        "********1234",
                        confirmedAt.minusHours(1)
                ),
                LocalDate.of(2026, 8, 3),
                confirmedAt
        );
    }
}
