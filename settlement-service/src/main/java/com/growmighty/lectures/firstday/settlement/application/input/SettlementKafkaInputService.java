package com.growmighty.lectures.firstday.settlement.application.input;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.OrderPaymentStatusChangedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectRefundProcessedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.dto.ProjectStatusChangedEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.kafka.inbox.KafkaInboxEvent;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataKafkaInboxEventRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataOrderPaymentFactRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataProjectOutcomeFactRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementKafkaInputService {

    private static final int SCHEMA_VERSION = 1;
    private static final String PROJECT_STATUS_CHANGED = "ProjectStatusChanged";
    private static final String ORDER_PAYMENT_STATUS_CHANGED = "OrderPaymentStatusChanged";
    private static final String PROJECT_REFUND_PROCESSED = "ProjectRefundProcessed";

    private final SpringDataKafkaInboxEventRepository inboxRepository;
    private final SpringDataProjectOutcomeFactRepository outcomeRepository;
    private final SpringDataOrderPaymentFactRepository paymentRepository;

    @Transactional
    public void saveProjectStatus(String key, ProjectStatusChangedEvent event) {
        validateProjectEvent(key, event);
        if (inboxRepository.existsById(event.eventId().toString())) {
            return;
        }

        ProjectOutcomeFact incoming = ProjectOutcomeFact.of(
                event.payload().projectId(),
                event.payload().creatorId(),
                requiredEnum(ProjectOutcomeFact.Outcome.class, event.payload().status(), "프로젝트 상태"),
                event.occurredAt().toInstant()
        );
        outcomeRepository.findById(incoming.projectId()).ifPresentOrElse(existing -> {
            if (!sameProjectOutcome(existing, incoming)) {
                throw new IllegalArgumentException("기존 프로젝트 결과 사실과 충돌합니다.");
            }
        }, () -> outcomeRepository.save(incoming));

        saveInbox(event.eventId(), event.eventType(), event.occurredAt());
    }

    @Transactional
    public void saveOrderPaymentStatus(String key, OrderPaymentStatusChangedEvent event) {
        validateOrderEvent(key, event);
        if (inboxRepository.existsById(event.eventId().toString())) {
            return;
        }

        OrderPaymentFact.Status status = requiredEnum(
                OrderPaymentFact.Status.class,
                event.payload().status(),
                "주문 결제 상태"
        );
        OrderPaymentFact existing = paymentRepository.findById(event.payload().orderId()).orElse(null);
        if (status == OrderPaymentFact.Status.COMPLETED) {
            saveCompletedPayment(existing, event);
        } else {
            saveCancelledPayment(existing, event);
        }
        saveInbox(event.eventId(), event.eventType(), event.occurredAt());
    }

    @Transactional
    public void saveProjectRefundProcessed(String key, ProjectRefundProcessedEvent event) {
        validateRefundResultEvent(key, event);
        if (inboxRepository.existsById(event.eventId().toString())) {
            return;
        }
        saveInbox(event.eventId(), event.eventType(), event.occurredAt());
    }

    private void saveCompletedPayment(OrderPaymentFact existing, OrderPaymentStatusChangedEvent event) {
        OrderPaymentFact incoming = OrderPaymentFact.completed(
                event.payload().orderId(),
                event.payload().pgOrderId(),
                event.payload().projectId(),
                Money.wons(event.payload().paymentAmount()),
                event.occurredAt().toInstant()
        );
        if (existing == null) {
            paymentRepository.save(incoming);
            return;
        }
        if (!sameCompletedPayment(existing, incoming)) {
            throw new IllegalArgumentException("기존 주문 결제 사실과 충돌합니다.");
        }
    }

    private static void saveCancelledPayment(OrderPaymentFact existing, OrderPaymentStatusChangedEvent event) {
        if (existing == null) {
            throw new IllegalArgumentException("완료 사실 없이 주문 결제를 취소할 수 없습니다.");
        }
        Money paymentAmount = Money.wons(event.payload().paymentAmount());
        Instant cancelledAt = event.occurredAt().toInstant();
        if (existing.status() == OrderPaymentFact.Status.CANCELLED) {
            if (!sameCancelledPayment(existing, event.payload().pgOrderId(), event.payload().projectId(), paymentAmount, cancelledAt)) {
                throw new IllegalArgumentException("기존 주문 결제 취소 사실과 충돌합니다.");
            }
            return;
        }
        existing.cancel(event.payload().pgOrderId(), event.payload().projectId(), paymentAmount, cancelledAt);
    }

    private void saveInbox(UUID eventId, String eventType, OffsetDateTime occurredAt) {
        inboxRepository.save(KafkaInboxEvent.processed(eventId, eventType, occurredAt.toInstant()));
    }

    private static void validateProjectEvent(String key, ProjectStatusChangedEvent event) {
        validateEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(), PROJECT_STATUS_CHANGED);
        if (event.payload() == null) {
            throw new IllegalArgumentException("프로젝트 상태 payload는 필수입니다.");
        }
        validateKey(key, event.payload().projectId(), "projectId");
        requirePositive(event.payload().creatorId(), "creatorId");
        requiredEnum(ProjectOutcomeFact.Outcome.class, event.payload().status(), "프로젝트 상태");
    }

    private static void validateOrderEvent(String key, OrderPaymentStatusChangedEvent event) {
        validateEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(), ORDER_PAYMENT_STATUS_CHANGED);
        if (event.payload() == null) {
            throw new IllegalArgumentException("주문 결제 상태 payload는 필수입니다.");
        }
        validateKey(key, event.payload().orderId(), "orderId");
        requirePositive(event.payload().projectId(), "projectId");
        if (event.payload().pgOrderId() == null || event.payload().pgOrderId().isBlank()) {
            throw new IllegalArgumentException("pgOrderId는 필수입니다.");
        }
        if (event.payload().paymentAmount() == null || event.payload().paymentAmount() <= 0) {
            throw new IllegalArgumentException("paymentAmount는 0보다 커야 합니다.");
        }
        requiredEnum(OrderPaymentFact.Status.class, event.payload().status(), "주문 결제 상태");
    }

    private static void validateRefundResultEvent(String key, ProjectRefundProcessedEvent event) {
        validateEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(), PROJECT_REFUND_PROCESSED);
        if (event.payload() == null) {
            throw new IllegalArgumentException("프로젝트 환불 처리 결과 payload는 필수입니다.");
        }
        validateKey(key, event.payload().settlementId(), "settlementId");
        validateOrderIds(event.payload().orderIds());
        if (event.payload().status() == null || event.payload().status().isBlank()) {
            throw new IllegalArgumentException("환불 처리 상태는 필수입니다.");
        }
    }

    private static void validateEnvelope(
            UUID eventId,
            String eventType,
            int schemaVersion,
            OffsetDateTime occurredAt,
            String expectedEventType
    ) {
        Objects.requireNonNull(eventId, "eventId는 필수입니다.");
        if (!expectedEventType.equals(eventType)) {
            throw new IllegalArgumentException("지원하지 않는 eventType입니다: " + eventType);
        }
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("지원하지 않는 schemaVersion입니다: " + schemaVersion);
        }
        Objects.requireNonNull(occurredAt, "occurredAt은 필수입니다.");
    }

    private static void validateKey(String key, Long payloadId, String field) {
        requirePositive(payloadId, field);
        try {
            if (!Objects.equals(Long.valueOf(key), payloadId)) {
                throw new IllegalArgumentException("Kafka key와 " + field + "가 일치하지 않습니다.");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Kafka key는 " + field + "여야 합니다.", exception);
        }
    }

    private static void validateKey(String key, String payloadId, String field) {
        if (payloadId == null || payloadId.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        if (!Objects.equals(key, payloadId)) {
            throw new IllegalArgumentException("Kafka key와 " + field + "가 일치하지 않습니다.");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + "는 양수여야 합니다.");
        }
    }

    private static void validateOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("환불 처리 결과에는 하나 이상의 orderId가 필요합니다.");
        }
        Set<Long> uniqueOrderIds = new HashSet<>();
        for (Long orderId : orderIds) {
            requirePositive(orderId, "orderId");
            if (!uniqueOrderIds.add(orderId)) {
                throw new IllegalArgumentException("환불 처리 결과의 orderId는 중복될 수 없습니다.");
            }
        }
    }

    private static <T extends Enum<T>> T requiredEnum(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 " + field + "입니다: " + value, exception);
        }
    }

    private static boolean sameProjectOutcome(ProjectOutcomeFact left, ProjectOutcomeFact right) {
        return Objects.equals(left.creatorId(), right.creatorId())
                && left.outcome() == right.outcome()
                && Objects.equals(left.occurredAt(), right.occurredAt());
    }

    private static boolean sameCompletedPayment(OrderPaymentFact left, OrderPaymentFact right) {
        return left.status() == OrderPaymentFact.Status.COMPLETED
                && Objects.equals(left.pgOrderId(), right.pgOrderId())
                && Objects.equals(left.projectId(), right.projectId())
                && Objects.equals(left.paymentAmount(), right.paymentAmount())
                && Objects.equals(left.completedAt(), right.completedAt());
    }

    private static boolean sameCancelledPayment(
            OrderPaymentFact existing,
            String pgOrderId,
            Long projectId,
            Money paymentAmount,
            Instant cancelledAt
    ) {
        return Objects.equals(existing.pgOrderId(), pgOrderId)
                && Objects.equals(existing.projectId(), projectId)
                && Objects.equals(existing.paymentAmount(), paymentAmount)
                && Objects.equals(existing.cancelledAt(), cancelledAt);
    }
}
