package com.growmighty.lectures.firstday.order.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.order.application.InternalOrderApiService;
import com.growmighty.lectures.firstday.order.infrastructure.kafka.dto.PaymentSingleResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSingleResultKafkaListener {

    private final InternalOrderApiService internalOrderApiService;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_SINGLE_RESULT,
            groupId = "order-service",
            containerFactory = "orderPaymentKafkaListenerContainerFactory")
    public void consume(PaymentSingleResultEvent event, Acknowledgment acknowledgment) {
        internalOrderApiService.applyPaymentStatus(event.orderId(), event.pgOrderId(), event.status());
        acknowledgment.acknowledge();
    }
}
