package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.CancelBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PayBody;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentApiData;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.PaymentDetailsApiData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "payment-service")
public interface PaymentFeignClient {

    @PostMapping("/internal/v1/payments/prepare")
    ApiResponse<PaymentApiData> pay(@RequestBody PayBody body);

    @GetMapping("/internal/v1/payments")
    ApiResponse<PaymentDetailsApiData> getPaymentByOrderId(@RequestParam("orderId") Long orderId);

    @PostMapping("/internal/v1/payments/cancel")
    ApiResponse<PaymentDetailsApiData> cancel(@RequestBody CancelBody body);
}
