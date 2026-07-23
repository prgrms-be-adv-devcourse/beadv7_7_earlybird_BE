package com.growmighty.lectures.firstday.payment.infrastructure.client.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service")
public interface OrderFeignClient {

    @PutMapping("/internal/v1/orders/{orderId}/payment-status")
    void updatePaymentStatus(
        @PathVariable Long orderId,
        @RequestBody OrderStatusRequest request
    );
}
