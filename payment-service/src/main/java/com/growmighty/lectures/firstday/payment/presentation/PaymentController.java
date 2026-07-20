package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.presentation.dto.PayRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    /** 결제 승인. TODO(팀): PG 확정 후 paymentKey 파라미터 추가 (API 명세서 §5, §10) */
    @PostMapping("/confirm")
    public PaymentResponse confirm(@RequestBody PayRequest request) {
        return PaymentResponse.from(paymentService.pay(request.orderId(), request.userId(), request.amount()));
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable Long paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(@PathVariable Long paymentId) {
        return PaymentResponse.from(paymentService.cancel(paymentId));
    }
}
