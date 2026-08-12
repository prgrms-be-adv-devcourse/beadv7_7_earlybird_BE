package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.presentation.dto.PayRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import com.growmighty.lectures.firstday.refund.application.RefundCancellationSagaOrchestrator;
import com.growmighty.lectures.firstday.refund.domain.RefundReason;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final RefundCancellationSagaOrchestrator refundCancellationSagaOrchestrator;

    /** 결제 승인.*/
    @PostMapping("/confirm")
    public PaymentResponse confirm(@Valid @RequestBody PayRequest request) {
        return PaymentResponse.from(paymentService.confirm(request.paymentKey(), request.pgOrderId(), request.amount()));
    }
    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@PathVariable Long paymentId) {
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }

    @GetMapping("/orders/{orderId}")
    public PaymentResponse getPaymentByOrderId(@PathVariable Long orderId) {
        return PaymentResponse.from(paymentService.getPaymentByOrderId(orderId));
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(@PathVariable Long paymentId) {
        refundCancellationSagaOrchestrator.cancel(
            paymentId,
            RefundReason.USER_CANCEL
        );
        return PaymentResponse.from(paymentService.getPayment(paymentId));
    }
}
