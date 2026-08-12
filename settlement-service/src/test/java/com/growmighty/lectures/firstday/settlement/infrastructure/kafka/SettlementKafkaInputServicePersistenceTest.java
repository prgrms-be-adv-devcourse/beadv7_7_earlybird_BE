package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.growmighty.lectures.firstday.settlement.application.input.SettlementKafkaInputService;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.config.JpaAuditingConfig;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.OrderPaymentStatusChangedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundProcessedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectStatusChangedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataKafkaInboxEventRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@Import({JpaAuditingConfig.class, SettlementKafkaInputService.class})
class SettlementKafkaInputServicePersistenceTest extends MySqlIntegrationTestSupport {

    @Autowired
    private SettlementKafkaInputService inputService;

    @Autowired
    private SpringDataKafkaInboxEventRepository inboxRepository;

    @Autowired
    private SpringDataProjectOutcomeFactRepository outcomeRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository paymentRepository;

    @Test
    @DisplayName("Project·Order 이벤트를 Inbox와 입력 사실에 한 번만 반영한다")
    void storesProjectAndOrderFactsIdempotently() {
        UUID projectEventId = UUID.randomUUID();
        inputService.saveProjectStatus("101", new ProjectStatusChangedEvent(
                projectEventId,
                "ProjectStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-31T18:00:00+09:00"),
                new ProjectStatusChangedEvent.Payload(101L, 9L, "SUCCEEDED")
        ));
        inputService.saveProjectStatus("101", new ProjectStatusChangedEvent(
                projectEventId,
                "ProjectStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-31T18:00:00+09:00"),
                new ProjectStatusChangedEvent.Payload(101L, 9L, "SUCCEEDED")
        ));

        inputService.saveOrderPaymentStatus("1001", new OrderPaymentStatusChangedEvent(
                UUID.randomUUID(),
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-15T13:20:10+09:00"),
                new OrderPaymentStatusChangedEvent.Payload(
                        1001L,
                        "PAY-01J2X8P4QW6YV0M3",
                        101L,
                        50_000L,
                        "COMPLETED"
                )
        ));
        inputService.saveOrderPaymentStatus("1001", new OrderPaymentStatusChangedEvent(
                UUID.randomUUID(),
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-18T09:05:00+09:00"),
                new OrderPaymentStatusChangedEvent.Payload(
                        1001L,
                        "PAY-01J2X8P4QW6YV0M3",
                        101L,
                        50_000L,
                        "CANCELLED"
                )
        ));

        ProjectOutcomeFact outcome = outcomeRepository.findById(101L).orElseThrow();
        OrderPaymentFact payment = paymentRepository.findById(1001L).orElseThrow();

        assertThat(inboxRepository.count()).isEqualTo(3);
        assertThat(outcome.outcome()).isEqualTo(ProjectOutcomeFact.Outcome.SUCCEEDED);
        assertThat(payment.status()).isEqualTo(OrderPaymentFact.Status.CANCELLED);
        assertThat(payment.paymentAmount().amount()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("Payment 환불 batch 결과는 Inbox에만 한 번 기록한다")
    void storesRefundResultOnlyInInbox() {
        UUID eventId = UUID.randomUUID();
        ProjectRefundProcessedEvent event = new ProjectRefundProcessedEvent(
                eventId,
                "ProjectRefundProcessed",
                1,
                OffsetDateTime.parse("2026-08-01T09:05:00+09:00"),
                new ProjectRefundProcessedEvent.Payload("101", List.of(1001L, 1002L), "COMPLETED")
        );

        inputService.saveProjectRefundProcessed("101", event);
        inputService.saveProjectRefundProcessed("101", event);

        assertThat(inboxRepository.count()).isEqualTo(1);
        assertThat(outcomeRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
    }
}
