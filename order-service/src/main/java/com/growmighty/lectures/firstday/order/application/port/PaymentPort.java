package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;

import java.math.BigDecimal;

public interface PaymentPort {

    PaymentResult pay(Long orderId, BigDecimal amount);

    void cancel(Long paymentId);
}
