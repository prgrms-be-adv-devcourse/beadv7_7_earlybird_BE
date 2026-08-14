package com.growmighty.lectures.firstday.settlement.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.OrderPaymentStatusChangedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundProcessedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundRequestedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectStatusChangedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataKafkaInboxEventRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import com.growmighty.lectures.firstday.settlement.support.MySqlIntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "settlement.refund-outbox.publish-fixed-delay=3600000"
})
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.PROJECT_STATUS_CHANGED,
                KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED,
                KafkaTopics.PAYMENT_BULK_CANCEL_RESULT,
                KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND,
                KafkaTopics.PROJECT_STATUS_CHANGED_DLT,
                KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED_DLT
        }
)
class SettlementKafkaIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private EmbeddedKafkaBroker kafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpringDataKafkaInboxEventRepository inboxRepository;

    @Autowired
    private SpringDataProjectOutcomeFactRepository outcomeRepository;

    @Autowired
    private SpringDataOrderPaymentFactRepository paymentRepository;

    @Autowired
    private ProjectRefundRequestedRepository outboxRepository;

    @Autowired
    private ProjectRefundRequestedKafkaPublisher publisher;

    @BeforeEach
    void waitForListenerAssignment() {
        listenerRegistry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(container, 1)
        );
    }

    @Test
    void storesProjectStatusInputIdempotently() throws Exception {
        long projectId = 81_001L;
        UUID projectEventId = UUID.randomUUID();

        send(KafkaTopics.PROJECT_STATUS_CHANGED, projectId, new ProjectStatusChangedEvent(
                projectEventId,
                "ProjectStatusChanged",
                1,
                OffsetDateTime.parse("2026-08-01T09:00:00+09:00"),
                new ProjectStatusChangedEvent.Payload(projectId, 701L, "SUCCEEDED")
        ));
        send(KafkaTopics.PROJECT_STATUS_CHANGED, projectId, new ProjectStatusChangedEvent(
                projectEventId,
                "ProjectStatusChanged",
                1,
                OffsetDateTime.parse("2026-08-01T09:00:00+09:00"),
                new ProjectStatusChangedEvent.Payload(projectId, 701L, "SUCCEEDED")
        ));

        assertThat(await(() -> inboxRepository.existsById(projectEventId.toString()))).isTrue();
        assertThat(outcomeRepository.findById(projectId).orElseThrow().outcome())
                .isEqualTo(ProjectOutcomeFact.Outcome.SUCCEEDED);
    }

    @Test
    void appliesOrderPaymentCancellation() throws Exception {
        long projectId = 81_005L;
        long orderId = 91_005L;
        UUID paymentCompletedEventId = UUID.randomUUID();
        UUID paymentCancelledEventId = UUID.randomUUID();

        send(KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED, orderId, new OrderPaymentStatusChangedEvent(
                paymentCompletedEventId,
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-15T13:20:10+09:00"),
                new OrderPaymentStatusChangedEvent.Payload(
                        orderId, "PG-91001", projectId, 50_000L, "COMPLETED"
                )
        ));
        send(KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED, orderId, new OrderPaymentStatusChangedEvent(
                paymentCancelledEventId,
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-07-18T09:05:00+09:00"),
                new OrderPaymentStatusChangedEvent.Payload(
                        orderId, "PG-91001", projectId, 50_000L, "CANCELLED"
                )
        ));

        assertThat(await(() -> paymentRepository.findById(orderId)
                .map(payment -> payment.status() == OrderPaymentFact.Status.CANCELLED)
                .orElse(false))).isTrue();
        assertThat(inboxRepository.existsById(paymentCompletedEventId.toString())).isTrue();
        assertThat(inboxRepository.existsById(paymentCancelledEventId.toString())).isTrue();
    }

    @Test
    void storesRefundProcessedInput() throws Exception {
        long projectId = 81_006L;
        long orderId = 91_006L;
        UUID refundProcessedEventId = UUID.randomUUID();

        send(KafkaTopics.PAYMENT_BULK_CANCEL_RESULT, projectId, new ProjectRefundProcessedEvent(
                refundProcessedEventId,
                "ProjectRefundProcessed",
                1,
                OffsetDateTime.parse("2026-08-02T10:00:00+09:00"),
                new ProjectRefundProcessedEvent.Payload(Long.toString(projectId), List.of(orderId), "COMPLETED")
        ));

        assertThat(await(() -> inboxRepository.existsById(refundProcessedEventId.toString()))).isTrue();
    }

    @Test
    void routesContractFailureToDltWithoutSavingInbox() throws Exception {
        long invalidProjectId = 81_002L;
        UUID invalidEventId = UUID.randomUUID();
        try (Consumer<String, String> dltConsumer = consumer("project-dlt")) {
            dltConsumer.subscribe(List.of(KafkaTopics.PROJECT_STATUS_CHANGED_DLT));
            send(KafkaTopics.PROJECT_STATUS_CHANGED, invalidProjectId, new ProjectStatusChangedEvent(
                    invalidEventId,
                    "UnknownEvent",
                    1,
                    OffsetDateTime.parse("2026-08-01T09:00:00+09:00"),
                    new ProjectStatusChangedEvent.Payload(invalidProjectId, 702L, "SUCCEEDED")
            ));

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    KafkaTopics.PROJECT_STATUS_CHANGED_DLT,
                    Duration.ofSeconds(10)
            );

            assertThat(record.value()).contains(invalidEventId.toString());
            assertThat(inboxRepository.existsById(invalidEventId.toString())).isFalse();
            assertThat(outcomeRepository.existsById(invalidProjectId)).isFalse();
        }
    }

    @Test
    void rollsBackPersistenceFailureThenRoutesItToDlt() throws Exception {
        long projectId = 81_004L;
        long savedOrderId = 91_004L;
        long conflictingOrderId = 91_005L;
        UUID savedEventId = UUID.randomUUID();
        UUID conflictingEventId = UUID.randomUUID();

        send(KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED, savedOrderId, new OrderPaymentStatusChangedEvent(
                savedEventId,
                "OrderPaymentStatusChanged",
                1,
                OffsetDateTime.parse("2026-08-01T09:00:00+09:00"),
                new OrderPaymentStatusChangedEvent.Payload(savedOrderId, "PG-ROLLBACK", projectId, 50_000L, "COMPLETED")
        ));
        assertThat(await(() -> paymentRepository.existsById(savedOrderId))).isTrue();

        try (Consumer<String, String> dltConsumer = consumer("order-dlt")) {
            dltConsumer.subscribe(List.of(KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED_DLT));
            send(KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED, conflictingOrderId, new OrderPaymentStatusChangedEvent(
                    conflictingEventId,
                    "OrderPaymentStatusChanged",
                    1,
                    OffsetDateTime.parse("2026-08-01T09:01:00+09:00"),
                    new OrderPaymentStatusChangedEvent.Payload(
                            conflictingOrderId, "PG-ROLLBACK", projectId, 50_000L, "COMPLETED"
                    )
            ));

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    dltConsumer,
                    KafkaTopics.ORDER_PAYMENT_STATUS_CHANGED_DLT,
                    Duration.ofSeconds(10)
            );

            assertThat(record.value()).contains(conflictingEventId.toString());
            assertThat(inboxRepository.existsById(conflictingEventId.toString())).isFalse();
            assertThat(paymentRepository.existsById(conflictingOrderId)).isFalse();
        }
    }

    @Test
    void publishesPendingOutboxAfterBrokerAcknowledgment() throws Exception {
        long projectId = 81_003L;
        ProjectRefundRequested request = request(projectId, 91_003L, "PG-91003");
        outboxRepository.save(request);

        try (Consumer<String, String> commandConsumer = consumer("refund-command")) {
            commandConsumer.subscribe(List.of(KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND));
            publisher.publishPending();

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    commandConsumer,
                    KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND,
                    Duration.ofSeconds(10)
            );
            ProjectRefundRequestedEvent event = objectMapper.readValue(
                    record.value(),
                    ProjectRefundRequestedEvent.class
            );

            assertThat(record.key()).isEqualTo(request.refundRequestId());
            assertThat(event.settlementId()).isEqualTo(request.refundRequestId());
            assertThat(event.payload().settlementId()).isEqualTo(request.refundRequestId());
            assertThat(event.payload().orderIds())
                    .containsExactly(91_003L);
            assertThat(outboxRepository.findByProjectId(projectId).orElseThrow().published()).isTrue();
        }
    }

    private void send(String topic, long key, Object event) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        ))) {
            producer.send(new ProducerRecord<>(topic, Long.toString(key), objectMapper.writeValueAsString(event))).get();
        }
    }

    private Consumer<String, String> consumer(String groupSuffix) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBroker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "settlement-kafka-test-" + groupSuffix + "-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }

    private static boolean await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static ProjectRefundRequested request(long projectId, long orderId, String pgOrderId) {
        Instant occurredAt = Instant.parse("2026-08-01T00:00:00Z");
        return ProjectRefundRequested.request(
                UUID.randomUUID().toString(),
                ProjectOutcomeFact.of(projectId, 703L, ProjectOutcomeFact.Outcome.FAILED, occurredAt),
                List.of(OrderPaymentFact.completed(
                        orderId,
                        pgOrderId,
                        projectId,
                        Money.wons(50_000),
                        occurredAt.minusSeconds(1)
                )),
                occurredAt
        );
    }
}
