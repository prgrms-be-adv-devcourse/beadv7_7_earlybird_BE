package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PayBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentApiData;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentDetailsApiData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentFeignClient {

    @PostMapping("/internal/v1/payments/prepare")
    ApiResponse<PaymentApiData> pay(@RequestBody PayBody body);

    @GetMapping("/api/v1/payments/orders/{orderId}")
    ApiResponse<PaymentDetailsApiData> getPaymentByOrderId(@PathVariable("orderId") Long orderId);

    @PostMapping("/api/v1/payments/{paymentId}/cancel")
    ApiResponse<PaymentDetailsApiData> cancel(@PathVariable("paymentId") Long paymentId);
}
