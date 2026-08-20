package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
class OrderPaymentResultHandler {

    private final OrderStockHandler stockHandler;
    private final OrderCartHandler cartHandler;

    @Autowired
    OrderPaymentResultHandler(OrderStockHandler stockHandler, OrderCartHandler cartHandler) {
        this.stockHandler = stockHandler;
        this.cartHandler = cartHandler;
    }

    OrderPaymentResultHandler(OrderStockHandler stockHandler) {
        this.stockHandler = stockHandler;
        this.cartHandler = null;
    }

    // 결제 결과에 대한 처리
    boolean apply(Order order, PaymentResult payment) {
        order.assignPgOrderId(payment.pgOrderId());
        Order.PaymentOutcome outcome = switch (payment.status()) {
            case SUCCESS -> Order.PaymentOutcome.SUCCESS;
            case FAILURE -> Order.PaymentOutcome.FAILURE;
            case CANCELLED, PENDING, UNKNOWN -> Order.PaymentOutcome.PENDING;
        };
        Order.PaymentHandling handling = order.handlePaymentOutcome(outcome);
        if (handling == Order.PaymentHandling.IGNORED) {
            return false;
        }
        if (handling == Order.PaymentHandling.COMPENSATION_REQUIRED) {
            compensatePaymentFailure(order);
            log.info("payment failed. orderId={}", order.getId());
            return false;
        }
        if (outcome == Order.PaymentOutcome.SUCCESS) {
            log.info("payment succeeded. orderId={}", order.getId());
            return true;
        }
        log.warn("payment result is unknown. orderId={}", order.getId());
        return false;
    }

    void compensatePaymentFailure(Order order) {
        try {
            stockHandler.releaseStock(order);
            order.markPaymentFailed();
        } catch (RuntimeException compensationFailure) {
            order.markPaymentCompensationPending();
        }
    }

    // 최종 결제까지 성공 시 cart에서 삭제
    void removeOrderedCartItems(Order order) {
        if (cartHandler != null) {
            cartHandler.removeOrderedItems(order);
        }
        log.info("ordered cart item cleanup requested. orderId={}, userId={}, rewardIds={}",
                order.getId(), order.getUserId(), rewardIds(order));
    }

    void removeInvalidCartReward(Long userId, Long rewardId) {
        if (cartHandler != null) {
            cartHandler.removeInvalidReward(userId, rewardId);
        }
    }

    private List<Long> rewardIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getRewardId)
                .toList();
    }
}
