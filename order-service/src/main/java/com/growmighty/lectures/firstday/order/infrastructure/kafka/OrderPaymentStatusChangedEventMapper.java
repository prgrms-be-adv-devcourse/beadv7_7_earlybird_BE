package com.growmighty.lectures.firstday.order.infrastructure.kafka;

import com.growmighty.lectures.firstday.order.application.dto.OrderPaymentStatusMessage;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import com.growmighty.lectures.firstday.order.infrastructure.kafka.dto.OrderPaymentStatusChangedEvent;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
class OrderPaymentStatusChangedEventMapper {
    private static final String EVENT_TYPE = "OrderPaymentStatusChanged";
    private static final int SCHEMA_VERSION = 1;

    OrderPaymentStatusChangedEvent map(OrderPaymentStatusMessage message) {
        return new OrderPaymentStatusChangedEvent(
                message.eventId(),
                EVENT_TYPE,
                SCHEMA_VERSION,
                OffsetDateTime.ofInstant(message.occurredAt(), ZoneOffset.UTC),
                new OrderPaymentStatusChangedEvent.Payload(
                        message.orderId(),
                        message.pgOrderId(),
                        message.projectId(),
                        message.paymentAmount().longValueExact(),
                        externalStatus(message.orderStatus())
                )
        );
    }

    private String externalStatus(OrderStatus status) {
        return switch (status) {
            case PAID -> "COMPLETED";
            case CANCELLED -> "CANCELLED";
            default -> throw new IllegalArgumentException("Unsupported outbound payment status=" + status);
        };
    }
}
