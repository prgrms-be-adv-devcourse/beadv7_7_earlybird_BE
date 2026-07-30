package com.growmighty.lectures.firstday.payment.application;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentConfirmationService  paymentConfirmationService;

    public void reconcile(PaymentGateway.PgPayment pgPayment) {
        paymentConfirmationService.reconcile(pgPayment);
    }
}
