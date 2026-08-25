package com.growmighty.lectures.firstday.settlement.application.input;

import com.growmighty.lectures.firstday.settlement.domain.model.Money;
import com.growmighty.lectures.firstday.settlement.domain.model.OrderPaymentFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectOutcomeFact;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectRefundRequested;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectRefundRequestedRepository;
import com.growmighty.lectures.firstday.settlement.domain.repository.SettlementKafkaInputRepository;
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

    private final SettlementKafkaInputRepository inputRepository;
    private final ProjectRefundRequestedRepository refundRequestedRepository;

    @Transactional
    public void saveProjectStatus(SettlementKafkaInput.ProjectStatusChanged event) {
        validateProjectEvent(event);
        if (!inputRepository.markProcessed(event.eventId(), event.eventType(), event.occurredAt().toInstant())) {
            return;
        }

        ProjectOutcomeFact incoming = ProjectOutcomeFact.of(
                event.projectId(),
                event.projectName(),
                event.creatorId(),
                requiredEnum(ProjectOutcomeFact.Outcome.class, event.status(), "프로젝트 상태"),
                event.occurredAt().toInstant()
        );
        inputRepository.findProjectOutcome(incoming.projectId()).ifPresentOrElse(existing -> {
            if (!sameProjectOutcome(existing, incoming)) {
                throw new IllegalArgumentException("기존 프로젝트 결과 사실과 충돌합니다.");
            }
        }, () -> inputRepository.save(incoming));
    }

    @Transactional
    public void saveOrderPaymentStatus(SettlementKafkaInput.OrderPaymentStatusChanged event) {
        validateOrderEvent(event);
        if (!inputRepository.markProcessed(event.eventId(), event.eventType(), event.occurredAt().toInstant())) {
            return;
        }

        OrderPaymentFact.Status status = requiredEnum(
                OrderPaymentFact.Status.class,
                event.status(),
                "주문 결제 상태"
        );
        OrderPaymentFact existing = inputRepository.findOrderPayment(event.orderId()).orElse(null);
        if (status == OrderPaymentFact.Status.COMPLETED) {
            saveCompletedPayment(existing, event);
        } else {
            saveCancelledPayment(existing, event);
        }
    }

    @Transactional
    public void saveProjectRefundProcessed(SettlementKafkaInput.ProjectRefundProcessed event) {
        validateRefundResultEvent(event);
        if (!inputRepository.markProcessed(event.eventId(), event.eventType(), event.occurredAt().toInstant())) {
            return;
        }
        Long refundRequestId = parseRefundRequestId(event.refundRequestId());
        ProjectRefundRequested request = refundRequestedRepository.findByRefundRequestId(refundRequestId)
                .orElseThrow(() -> new IllegalArgumentException("환불 요청을 찾을 수 없습니다."));
        request.recordPaymentResult(event.status(), event.occurredAt().toInstant(), event.orderIds());
        refundRequestedRepository.save(request);
    }

    private void saveCompletedPayment(OrderPaymentFact existing, SettlementKafkaInput.OrderPaymentStatusChanged event) {
        OrderPaymentFact incoming = OrderPaymentFact.completed(
                event.orderId(),
                event.pgOrderId(),
                event.projectId(),
                Money.wons(event.paymentAmount()),
                event.occurredAt().toInstant()
        );
        if (existing == null) {
            inputRepository.save(incoming);
            return;
        }
        if (!sameCompletedPayment(existing, incoming)) {
            throw new IllegalArgumentException("기존 주문 결제 사실과 충돌합니다.");
        }
    }

    private static void saveCancelledPayment(OrderPaymentFact existing, SettlementKafkaInput.OrderPaymentStatusChanged event) {
        if (existing == null) {
            throw new IllegalArgumentException("완료 사실 없이 주문 결제를 취소할 수 없습니다.");
        }
        Money paymentAmount = Money.wons(event.paymentAmount());
        Instant cancelledAt = event.occurredAt().toInstant();
        if (existing.status() == OrderPaymentFact.Status.CANCELLED) {
            if (!sameCancelledPayment(existing, event.pgOrderId(), event.projectId(), paymentAmount, cancelledAt)) {
                throw new IllegalArgumentException("기존 주문 결제 취소 사실과 충돌합니다.");
            }
            return;
        }
        existing.cancel(event.pgOrderId(), event.projectId(), paymentAmount, cancelledAt);
    }

    private static void validateProjectEvent(SettlementKafkaInput.ProjectStatusChanged event) {
        validateEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(), PROJECT_STATUS_CHANGED);
        validateKey(event.key(), event.projectId(), "projectId");
        if (event.projectName() == null || event.projectName().isBlank()) {
            throw new IllegalArgumentException("projectName은 필수입니다.");
        }
        requirePositive(event.creatorId(), "creatorId");
        requiredEnum(ProjectOutcomeFact.Outcome.class, event.status(), "프로젝트 상태");
    }

    private static void validateOrderEvent(SettlementKafkaInput.OrderPaymentStatusChanged event) {
        validateEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(), ORDER_PAYMENT_STATUS_CHANGED);
        validateKey(event.key(), event.orderId(), "orderId");
        requirePositive(event.projectId(), "projectId");
        if (event.pgOrderId() == null || event.pgOrderId().isBlank()) {
            throw new IllegalArgumentException("pgOrderId는 필수입니다.");
        }
        if (event.paymentAmount() == null || event.paymentAmount() <= 0) {
            throw new IllegalArgumentException("paymentAmount는 0보다 커야 합니다.");
        }
        requiredEnum(OrderPaymentFact.Status.class, event.status(), "주문 결제 상태");
    }

    private static void validateRefundResultEvent(SettlementKafkaInput.ProjectRefundProcessed event) {
        validateEnvelope(event.eventId(), event.eventType(), event.schemaVersion(), event.occurredAt(), PROJECT_REFUND_PROCESSED);
        validateKey(event.key(), event.refundRequestId(), "refundRequestId");
        validateOrderIds(event.orderIds());
        requiredEnum(RefundProcessingStatus.class, event.status(), "환불 처리 상태");
    }

    private static Long parseRefundRequestId(String value) {
        try {
            Long parsed = Long.valueOf(value);
            requirePositive(parsed, "refundRequestId");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("refundRequestId는 양의 숫자여야 합니다.", exception);
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
        return Objects.equals(left.projectName(), right.projectName())
                && Objects.equals(left.creatorId(), right.creatorId())
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

    private enum RefundProcessingStatus {
        COMPLETED,
        FAILED
    }
}
