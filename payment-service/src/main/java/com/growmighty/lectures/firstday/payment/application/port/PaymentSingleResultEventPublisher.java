package com.growmighty.lectures.firstday.payment.application.port;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentStatusChangedEvent;

public interface PaymentSingleResultEventPublisher {
    void publish(PaymentStatusChangedEvent event);
}
