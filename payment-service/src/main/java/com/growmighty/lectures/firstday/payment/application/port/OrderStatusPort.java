package com.growmighty.lectures.firstday.payment.application.port;

import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;

public interface OrderStatusPort {
    void notifyStatus(Long orderId, PaymentStatus status);
}
