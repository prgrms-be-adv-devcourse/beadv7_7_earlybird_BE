package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.dto.OrderPaymentStatusMessage;

record OrderPaymentStatusOutboxTask(
        Long outboxId,
        int retryCount,
        OrderPaymentStatusMessage message
) {
}
