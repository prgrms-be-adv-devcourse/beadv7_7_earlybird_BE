package com.growmighty.lectures.firstday.payment.presentation;


import com.growmighty.lectures.firstday.payment.application.PaymentService;
import com.growmighty.lectures.firstday.payment.application.dto.PaymentPreparationInfo;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareRequest;
import com.growmighty.lectures.firstday.payment.presentation.dto.PaymentPrepareResponse;
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
    public PaymentPrepareResponse prepare(@Valid @RequestBody PaymentPrepareRequest request) {
        PaymentPreparationInfo payment = paymentService.prepare(
            request.userId(),
            request.orderId(),
            request.amount()
        );
        return PaymentPrepareResponse.from(payment);
    }
}
