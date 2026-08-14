package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.dto.OrderPaymentStatusMessage;

public interface OrderPaymentStatusEventPublisher {
    void publish(OrderPaymentStatusMessage message);
}
