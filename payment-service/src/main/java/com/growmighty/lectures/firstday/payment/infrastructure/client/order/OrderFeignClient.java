package com.growmighty.lectures.firstday.payment.infrastructure.client.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderFeignClient {

    @PutMapping("/internal/v1/orders/{orderId}/payment-status")
    void updatePaymentStatus(
        @PathVariable UUID orderId,
        @RequestBody OrderStatusRequest request
    );
}
