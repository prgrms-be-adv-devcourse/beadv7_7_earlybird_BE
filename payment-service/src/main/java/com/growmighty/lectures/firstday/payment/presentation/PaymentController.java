package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.presentation.dto.PayRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentResponse> pay(@RequestBody PayRequest request) {
        return ApiResponse.ok(PaymentResponse.from(paymentService.pay(request.amount())));
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentResponse> getPayment(@PathVariable Long paymentId) {
        return ApiResponse.ok(PaymentResponse.from(paymentService.getPayment(paymentId)));
    }

    @PostMapping("/{paymentId}/cancel")
    public ApiResponse<PaymentResponse> cancel(@PathVariable Long paymentId) {
        return ApiResponse.ok(PaymentResponse.from(paymentService.cancel(paymentId)));
    }
}
