package com.growmighty.lectures.firstday.payment.application.port;

import com.growmighty.lectures.firstday.payment.application.dto.PaymentSingleResultEvent;

public interface PaymentSingleResultEventPublisher {
    void publish(PaymentSingleResultEvent event);
}
