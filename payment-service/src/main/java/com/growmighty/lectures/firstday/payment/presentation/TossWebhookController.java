package com.growmighty.lectures.firstday.payment.presentation;

import com.growmighty.lectures.firstday.payment.application.PaymentConfirmationService;
import com.growmighty.lectures.firstday.payment.application.PaymentGateway;
import com.growmighty.lectures.firstday.payment.infrastructure.toss.dto.TossWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/toss")
public class TossWebhookController {
    private final PaymentGateway paymentGateway;
    private final PaymentConfirmationService paymentConfirmationService;

    @PostMapping("/webhook")
    public void receive(@RequestBody TossWebhookRequest request) {
        String paymentKey = request.data().paymentKey();

        PaymentGateway.PgPayment pgPayment = paymentGateway.getPayment(paymentKey);
        paymentConfirmationService.reconcile(pgPayment);
    }
}
