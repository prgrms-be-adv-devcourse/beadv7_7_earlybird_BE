package com.growmighty.lectures.firstday.order.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.order.application.InternalOrderApiService;
import com.growmighty.lectures.firstday.order.infrastructure.kafka.dto.PaymentSingleResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSingleResultKafkaListener {

    private final InternalOrderApiService internalOrderApiService;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_SINGLE_RESULT,
            properties = "spring.json.type.mapping=paymentSingleResult:com.growmighty.lectures.firstday.order.infrastructure.kafka.dto.PaymentSingleResultEvent")
    public void consume(PaymentSingleResultEvent event) {
        internalOrderApiService.applyPaymentStatus(event.orderId(), event.pgOrderId(), event.status());
    }
}
