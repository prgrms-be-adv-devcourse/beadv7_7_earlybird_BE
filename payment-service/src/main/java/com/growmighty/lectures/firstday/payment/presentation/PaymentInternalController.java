package com.growmighty.lectures.firstday.payment.presentation;


import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentInfo;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("internal/v1/payments")
public class PaymentInternalController {

    private final PaymentService paymentService;

    @PostMapping("/prepare")
    public PaymentResponse prepare(@Valid @RequestBody PaymentPrepareRequest request) {
        PaymentInfo payment = paymentService.prepare(
            request.orderId(),
            request.pgOrderId(),
            request.userId(),
            request.amount()
        );
        return PaymentResponse.from(payment);
    }
}
