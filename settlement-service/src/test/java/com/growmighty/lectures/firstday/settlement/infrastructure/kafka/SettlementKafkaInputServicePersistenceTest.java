package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInput;
import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInputService;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.ProjectRefundRequestedRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter.SettlementKafkaInputRepositoryAdapter;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataKafkaInboxEventRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({
        JpaAuditingConfig.class,
        SettlementKafkaInputService.class,
        SettlementKafkaInputRepositoryAdapter.class,
        ProjectRefundRequestedRepositoryAdapter.class
})
class SettlementKafkaInputServicePersistenceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private SettlementKafkaInputService inputService;

    @Autowired
    private SpringDataKafkaInboxEventRepository inboxRepository;

    @Autowired
    private SpringDataProjectOutcomeFactRepository outcomeRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository paymentRepository;

    @Autowired
    private ProjectRefundRequestedRepository refundRequestedRepository;

    @Test
    @DisplayName("Project·Order 이벤트를 Inbox와 입력 사실에 한 번만 반영한다")
    void storesProjectAndOrderFactsIdempotently() {
        UUID projectEventId = UUID.randomUUID();
        inputService.saveProjectStatus(new SettlementKafkaInput.ProjectStatusChanged(
                "101",
                projectEventId,
                "ProjectStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-31T18:00:00+09:00"),
                101L, "프로젝트 101", 9L, "SUCCEEDED"
        ));
        inputService.saveProjectStatus(new SettlementKafkaInput.ProjectStatusChanged(
                "101",
                projectEventId,
                "ProjectStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-31T18:00:00+09:00"),
                101L, "프로젝트 101", 9L, "SUCCEEDED"
        ));

        inputService.saveOrderPaymentStatus(new SettlementKafkaInput.OrderPaymentStatusChanged(
                "1001",
                UUID.randomUUID(),
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-15T13:20:10+09:00"),
                1001L, "PAY-01J2X8P4QW6YV0M3", 101L, 50_000L, "COMPLETED"
        ));
        inputService.saveOrderPaymentStatus(new SettlementKafkaInput.OrderPaymentStatusChanged(
                "1001",
                UUID.randomUUID(),
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-18T09:05:00+09:00"),
                1001L, "PAY-01J2X8P4QW6YV0M3", 101L, 50_000L, "CANCELLED"
        ));

        ProjectOutcomeFact outcome = outcomeRepository.findById(101L).orElseThrow();
        OrderPaymentFact payment = paymentRepository.findById(1001L).orElseThrow();

        assertThat(inboxRepository.count()).isEqualTo(3);
        assertThat(outcome.projectName()).isEqualTo("프로젝트 101");
        assertThat(outcome.outcome()).isEqualTo(ProjectOutcomeFact.Outcome.SUCCEEDED);
        assertThat(payment.status()).isEqualTo(OrderPaymentFact.Status.CANCELLED);
        assertThat(payment.paymentAmount().amount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Payment 환불 batch 결과를 Inbox와 기존 Outbox에 한 번 기록한다")
    void storesRefundResultInInboxAndOutbox() {
        ProjectRefundRequested request = refundRequest(94_000_101L, 101L, List.of(1001L, 1002L));
        refundRequestedRepository.save(request);
        UUID eventId = UUID.randomUUID();
        SettlementKafkaInput.ProjectRefundProcessed event = new SettlementKafkaInput.ProjectRefundProcessed(
                request.refundRequestId().toString(),
                eventId,
                "ProjectRefundProcessed",
                1,
                OffsetDateTime.parse("2026-08-01T09:05:00+09:00"),
                request.refundRequestId().toString(), List.of(1001L, 1002L), "COMPLETED"
        );

        inputService.saveProjectRefundProcessed(event);
        inputService.saveProjectRefundProcessed(event);

        assertThat(inboxRepository.count()).isEqualTo(1);
        ProjectRefundRequested stored = refundRequestedRepository.findByRefundRequestId(request.refundRequestId())
                .orElseThrow();
        assertThat(stored.paymentResultStatus()).isEqualTo("COMPLETED");
        assertThat(stored.paymentResultAt()).isEqualTo(event.occurredAt().toInstant());
        assertThat(stored.failedOrderIds()).isEmpty();
        assertThat(outcomeRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    @DisplayName("실패한 환불 batch 결과의 주문 목록만 기존 Outbox에 기록한다")
    void storesFailedRefundOrderIdsInOutbox() {
        ProjectRefundRequested request = refundRequest(94_000_103L, 103L, List.of(1004L, 1005L));
        refundRequestedRepository.save(request);
        SettlementKafkaInput.ProjectRefundProcessed event = refundResult(
                request.refundRequestId().toString(),
                UUID.randomUUID(),
                "FAILED",
                "2026-08-01T09:05:00+09:00",
                List.of(1005L)
        );

        inputService.saveProjectRefundProcessed(event);

        ProjectRefundRequested stored = refundRequestedRepository.findByRefundRequestId(request.refundRequestId())
                .orElseThrow();
        assertThat(stored.paymentResultStatus()).isEqualTo("FAILED");
        assertThat(stored.failedOrderIds()).containsExactly(1005L);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("환불 요청에 없는 실패 주문은 기록하지 않고 Inbox도 롤백한다")
    void rejectsFailedOrderOutsideRefundRequest() {
        ProjectRefundRequested request = refundRequest(94_000_104L, 104L, List.of(1006L));
        refundRequestedRepository.save(request);
        UUID eventId = UUID.randomUUID();

        assertThatThrownBy(() -> inputService.saveProjectRefundProcessed(refundResult(
                request.refundRequestId().toString(), eventId, "FAILED", "2026-08-01T09:05:00+09:00", List.of(1007L)
        ))).isInstanceOf(IllegalArgumentException.class);

        ProjectRefundRequested stored = refundRequestedRepository.findByRefundRequestId(request.refundRequestId())
                .orElseThrow();
        assertThat(stored.paymentResultStatus()).isNull();
        assertThat(stored.failedOrderIds()).isEmpty();
        assertThat(inboxRepository.existsById(eventId.toString())).isFalse();
    }

    @Test
    @DisplayName("다른 결과가 같은 환불 요청을 덮어쓰지 않고 Inbox도 함께 롤백한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsConflictingRefundResult() {
        ProjectRefundRequested request = refundRequest(94_000_102L, 102L, List.of(1003L));
        refundRequestedRepository.save(request);
        inputService.saveProjectRefundProcessed(refundResult(
                request.refundRequestId().toString(), UUID.randomUUID(), "COMPLETED", "2026-08-01T09:05:00+09:00", List.of(1003L)
        ));

        assertThatThrownBy(() -> inputService.saveProjectRefundProcessed(refundResult(
                request.refundRequestId().toString(), UUID.randomUUID(), "FAILED", "2026-08-01T09:06:00+09:00", List.of(1003L)
        ))).isInstanceOf(IllegalStateException.class);

        ProjectRefundRequested stored = refundRequestedRepository.findByRefundRequestId(request.refundRequestId())
                .orElseThrow();
        assertThat(stored.paymentResultStatus()).isEqualTo("COMPLETED");
        assertThat(inboxRepository.count()).isEqualTo(1);
    }

    private static SettlementKafkaInput.ProjectRefundProcessed refundResult(
            String refundRequestId,
            UUID eventId,
            String status,
            String occurredAt,
            List<Long> orderIds
    ) {
        return new SettlementKafkaInput.ProjectRefundProcessed(
                refundRequestId,
                eventId,
                "ProjectRefundProcessed",
                1,
                OffsetDateTime.parse(occurredAt),
                refundRequestId,
                orderIds,
                status
        );
    }

    private static ProjectRefundRequested refundRequest(Long refundRequestId, Long projectId, List<Long> orderIds) {
        Instant occurredAt = Instant.parse("2026-08-01T00:00:00Z");
        return ProjectRefundRequested.request(
                refundRequestId,
                ProjectOutcomeFact.of(projectId, "프로젝트 " + projectId, 9L, ProjectOutcomeFact.Outcome.FAILED, occurredAt),
                orderIds.stream().map(orderId -> OrderPaymentFact.completed(
                        orderId,
                        "PG-" + orderId,
                        projectId,
                        com.growmighty.lectures.firstday.settlement.domain.model.Money.wons(50_000),
                        occurredAt.minusSeconds(1)
                )).toList(),
                occurredAt
        );
    }
}
