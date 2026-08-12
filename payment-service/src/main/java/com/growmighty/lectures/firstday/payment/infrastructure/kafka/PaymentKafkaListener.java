package com.growmighty.lectures.firstday.payment.infrastructure.kafka;

import com.growmighty.lectures.firstday.common.kafka.KafkaTopics;
import com.growmighty.lectures.firstday.payment.infrastructure.kafka.dto.PaymentBulkCancelCommand;
import com.growmighty.lectures.firstday.refund.application.BulkRefundService;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentKafkaListener {

    private final BulkRefundService bulkRefundService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_BULK_CANCEL_COMMAND)
    public void onBulkCancelCommand(PaymentBulkCancelCommand command) {
        bulkRefundService.plan(
            command.settlementId(),
            command.orderIds(),
            RefundReason.fromCode(command.reason())
        );
    }
}
