package com.growmighty.lectures.firstday.payment.presentation;


import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentCancelRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareResponse;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import com.growmighty.lectures.firstday.refund.application.RefundCancellationSagaOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
    @RequestMapping("internal/v1/payments")
    public class PaymentInternalController {

        private final PaymentService paymentService;
        private final RefundCancellationSagaOrchestrator refundCancellationSagaOrchestrator;

    @PostMapping("/prepare")
    public PaymentPrepareResponse prepare(@Valid @RequestBody PaymentPrepareRequest request) {
        PaymentPreparationInfo payment = paymentService.prepare(
            request.userId(),
            request.orderId(),
            request.amount()
        );
        return PaymentPrepareResponse.from(payment);
    }

    @PostMapping("/cancel")
    public PaymentResponse cancel(@Valid @RequestBody PaymentCancelRequest request) {
        refundCancellationSagaOrchestrator.cancel(request.orderId(), request.paymentId());
        return PaymentResponse.from(paymentService.getPaymentForInternal(request.paymentId()));
    }

    @GetMapping
    public PaymentResponse getPaymentByOrderId(@RequestParam Long orderId) {
        return PaymentResponse.from(paymentService.getPaymentByOrderIdInternal(orderId));
    }
}
