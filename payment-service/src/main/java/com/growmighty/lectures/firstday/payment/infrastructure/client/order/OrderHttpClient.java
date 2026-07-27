package com.growmighty.lectures.firstday.payment.infrastructure.client.order;

import com.growmighty.lectures.firstday.payment.application.port.OrderStatusPort;
import com.growmighty.lectures.firstday.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderHttpClient implements OrderStatusPort {
    private final OrderFeignClient orderFeignClient;


    @Override
    public void notifyStatus(UUID orderId, PaymentStatus status) {
        orderFeignClient.updatePaymentStatus(
            orderId,
            new OrderStatusRequest(status.name())
        );

    }
}
