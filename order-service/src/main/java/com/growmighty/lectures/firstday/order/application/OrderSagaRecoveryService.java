package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSagaRecoveryService {

    private final OrderRepository orderRepository;
    private final PaymentPort paymentPort;
    private final OrderRemoteCallExecutor remoteCalls;
    private final OrderStockHandler stockHandler;
    private final OrderPaymentResultHandler paymentResultHandler;

    public void recoverPendingOrders() {
        List<OrderStatus> statuses = List.of(OrderStatus.STOCK_PENDING, OrderStatus.PAYMENT_PENDING,
                OrderStatus.STOCK_COMPENSATION_PENDING, OrderStatus.PAYMENT_COMPENSATION_PENDING);
        for (Order order : orderRepository.findByStatusIn(statuses)) {
            try {
                recoverPendingOrder(order);
            } catch (OptimisticLockingFailureException conflict) {
                log.info("order saga recovery skipped after concurrent update. orderId={}", order.getId());
            } catch (RuntimeException failure) {
                log.warn("order saga recovery remains pending. orderId={}, status={}",
                        order.getId(), order.getStatus(), failure);
            }
        }
    }

    private void recoverPendingOrder(Order order) {
        switch (order.getStatus()) {
            case STOCK_PENDING -> recoverStock(order);
            case PAYMENT_PENDING -> recoverPayment(order);
            case STOCK_COMPENSATION_PENDING -> {
                stockHandler.releaseStock(order);
                order.markStockReservationFailed();
                orderRepository.save(order);
            }
            case PAYMENT_COMPENSATION_PENDING -> {
                stockHandler.releaseStock(order);
                order.markPaymentFailed();
                orderRepository.save(order);
            }
            default -> { }
        }
    }

    private void recoverStock(Order order) {
        List<OrderItem> confirmedItems = new ArrayList<>();
        try {
            stockHandler.reserveStock(order, confirmedItems);
        } catch (RuntimeException failure) {
            if (remoteCalls.isTechnical(failure)) {
                return;
            }
            stockHandler.compensateStockFailure(order, confirmedItems);
            orderRepository.save(order);
            if (failure instanceof InvalidCartRewardException invalidReward) {
                paymentResultHandler.removeInvalidCartReward(order.getUserId(), invalidReward.rewardId());
            }
            return;
        }
        order.markPaymentRequested();
        Order paymentOrder = orderRepository.save(order);
        try {
            PaymentResult result = remoteCalls.execute("payment-pay",
                    () -> paymentPort.pay(paymentOrder.getId(), paymentOrder.getUserId(),
                            paymentOrder.getTotalAmount().getValue()));
            boolean orderCompleted = paymentResultHandler.apply(paymentOrder, result);
            orderRepository.save(paymentOrder);
            if (orderCompleted) {
                paymentResultHandler.removeOrderedCartItems(paymentOrder);
            }
            return;
        } catch (RuntimeException failure) {
            if (remoteCalls.isTechnical(failure)) {
                paymentOrder.markPaymentPending();
            } else {
                paymentResultHandler.compensatePaymentFailure(paymentOrder);
            }
        }
        orderRepository.save(paymentOrder);
    }

    private void recoverPayment(Order order) {
        log.warn("payment status lookup by orderId is unavailable; awaiting payment callback. orderId={}", order.getId());
        orderRepository.save(order);
    }
}
