package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort.CancellationResult;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderCancellationOrchestrationService {
    private final OrderRepository orderRepository;
    private final PaymentPort paymentPort;
    private final OrderRemoteCallExecutor remoteCalls;
    private final OrderCancellationPersistenceService cancellationPersistenceService;

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
        if (order.isCancelled()) {
            return cancellationPersistenceService.finalizeCancellation(orderId, order.getPgOrderId());
        }

        order.validateCancellationAllowed();
        PaymentResult payment = remoteCalls.execute("payment-get-by-order",
                () -> paymentPort.getPaymentResult(order.getId()));
        if (payment == null || payment.status() == PaymentResult.Status.UNKNOWN) {
            throw new ServiceUnavailableException("Payment cancellation status is unavailable. orderId=" + orderId);
        }
        validatePaymentIdentity(order, payment);
        if (payment.status() == PaymentResult.Status.CANCELLED) {
            return cancellationPersistenceService.finalizeCancellation(orderId, payment.pgOrderId());
        }
        if (payment.status() != PaymentResult.Status.SUCCESS || payment.paymentId() == null) {
            throw new IllegalStateException("Paid payment was not found for cancellation. orderId=" + orderId);
        }

        Long paymentId = payment.paymentId();
        CancellationResult cancellation = remoteCalls.execute("payment-cancel",
                () -> paymentPort.cancel(paymentId, order.getTotalAmount().getValue()));
        if (cancellation == null || cancellation.status() == PaymentResult.Status.UNKNOWN) {
            throw new ServiceUnavailableException("Payment cancellation result is unavailable. orderId=" + orderId);
        }
        if (cancellation.status() != PaymentResult.Status.SUCCESS) {
            throw new IllegalStateException("Payment cancellation failed or pending. orderId=" + orderId);
        }
        if (!paymentId.equals(cancellation.paymentId())
                || !order.getId().equals(cancellation.orderId())) {
            throw new ServiceUnavailableException("Payment cancellation response is inconsistent. orderId=" + orderId);
        }
        return cancellationPersistenceService.finalizeCancellation(orderId, payment.pgOrderId());
    }

    private void validatePaymentIdentity(Order order, PaymentResult payment) {
        if (payment.amount() == null
                || order.getTotalAmount().getValue().compareTo(payment.amount()) != 0) {
            throw new ServiceUnavailableException("Payment amount mismatch. orderId=" + order.getId());
        }
        if (payment.pgOrderId() != null && order.getPgOrderId() != null
                && !order.getPgOrderId().equals(payment.pgOrderId())) {
            throw new ServiceUnavailableException("Payment PG order ID mismatch. orderId=" + order.getId());
        }
    }
}
