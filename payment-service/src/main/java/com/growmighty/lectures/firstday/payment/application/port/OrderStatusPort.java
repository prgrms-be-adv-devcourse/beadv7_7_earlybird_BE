package com.growmighty.lectures.firstday.payment.application.port;

import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;

import java.util.UUID;

public interface OrderStatusPort {
    void notifyStatus(UUID orderId, PaymentStatus status);
}
