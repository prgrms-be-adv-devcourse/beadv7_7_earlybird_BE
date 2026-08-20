package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.presentation.dto.PayRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import com.growmighty.lectures.firstday.refund.application.RefundCancellationSagaOrchestrator;
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
    public PaymentResponse confirm(
        @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
        @Valid @RequestBody PayRequest request
    ) {
        return PaymentResponse.from(paymentService.confirm(requesterId, request.paymentKey(), request.pgOrderId(), request.amount()));
    }
    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(
        @PathVariable Long paymentId,
        @RequestHeader(JwtHeaders.USER_ID) Long requesterId
    ) {
        return PaymentResponse.from(paymentService.getPayment(paymentId, requesterId));
    }

    @GetMapping("/orders/{orderId}")
    public PaymentResponse getPaymentByOrderId(
        @PathVariable Long orderId,
        @RequestHeader(JwtHeaders.USER_ID) Long requesterId
    ) {
        return PaymentResponse.from(paymentService.getPaymentByOrderId(orderId,  requesterId));
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentResponse cancel(
        @PathVariable Long paymentId,
        @RequestHeader(JwtHeaders.USER_ID) Long requesterId
    ) {
        refundCancellationSagaOrchestrator.cancelByUser(
            paymentId,
            requesterId
        );
        return PaymentResponse.from(paymentService.getPayment(paymentId, requesterId));
    }
}
