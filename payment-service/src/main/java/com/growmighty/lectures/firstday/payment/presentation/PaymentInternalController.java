package com.growmighty.lectures.firstday.payment.presentation;


import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareResponse;
import com.growmighty.lectures.firstday.refund.Presentation.dto.PaymentRefundRequest;
import com.growmighty.lectures.firstday.refund.Presentation.dto.PaymentRefundResponse;
import com.growmighty.lectures.firstday.refund.application.RefundService;
import com.growmighty.lectures.firstday.refund.domain.Refund;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("internal/v1/payments")
public class PaymentInternalController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @PostMapping("/prepare")
    public PaymentPrepareResponse prepare(@Valid @RequestBody PaymentPrepareRequest request) {
        PaymentPreparationInfo payment = paymentService.prepare(
            request.orderId(),
            request.amount()
        );
        return PaymentPrepareResponse.from(payment);
    }

    @PostMapping("/orders/{orderId}/refund")
    public PaymentRefundResponse refundResponse(@PathVariable UUID orderId, @Valid @RequestBody PaymentRefundRequest request) {
        Refund refund = refundService.refund(orderId, request.reason());
        return  PaymentRefundResponse.from(refund);
    }
}
