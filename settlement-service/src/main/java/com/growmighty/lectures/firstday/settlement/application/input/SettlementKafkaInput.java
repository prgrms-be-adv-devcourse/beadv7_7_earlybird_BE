package com.growmighty.lectures.firstday.settlement.application.input;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SettlementKafkaInput {

    record ProjectStatusChanged(
            String key,
            UUID eventId,
            String eventType,
            int schemaVersion,
            OffsetDateTime occurredAt,
            Long projectId,
            String projectName,
            Long creatorId,
            String status
    ) implements SettlementKafkaInput {
    }

    record OrderPaymentStatusChanged(
            String key,
            UUID eventId,
            String eventType,
            int schemaVersion,
            OffsetDateTime occurredAt,
            Long orderId,
            String pgOrderId,
            Long projectId,
            Long paymentAmount,
            String status
    ) implements SettlementKafkaInput {
    }

    record ProjectRefundProcessed(
            String key,
            UUID eventId,
            String eventType,
            int schemaVersion,
            OffsetDateTime occurredAt,
            String refundRequestId,
            List<Long> orderIds,
            String status
    ) implements SettlementKafkaInput {
    }
}
