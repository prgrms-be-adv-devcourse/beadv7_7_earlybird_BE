package com.growmighty.lectures.firstday.payment.application;


import com.growmighty.lectures.firstday.payment.application.port.OrderStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentConfirmationService  paymentConfirmationService;
    private final OrderStatusPort  orderStatusPort;

    public void reconcile(PaymentGateway.PgPayment pgPayment) {
        paymentConfirmationService.reconcile(pgPayment)
            .ifPresent(payment -> orderStatusPort.notifyStatus(
                payment.orderId(),
                payment.status()
            ));
    }
}
