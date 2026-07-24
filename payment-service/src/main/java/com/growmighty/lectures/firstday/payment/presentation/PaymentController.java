package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.presentation.dto.PayRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    /** 결제 승인.*/
    @PostMapping("/confirm")
    public PaymentResponse confirm(@Valid @RequestBody PayRequest request) {
        return PaymentResponse.from(paymentService.confirm(request.paymentKey(), request.pgOrderId(), request.amount()));
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
