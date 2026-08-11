package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
class OrderPaymentResultHandler {

    private final OrderStockHandler stockHandler;

    // 결제 결과에 대한 처리
    void apply(Order order, PaymentResult payment) {
        Order.PaymentOutcome outcome = switch (payment.status()) {
            case SUCCESS -> Order.PaymentOutcome.SUCCESS;
            case FAILURE -> Order.PaymentOutcome.FAILURE;
            case PENDING, UNKNOWN -> Order.PaymentOutcome.PENDING;
        };
        Order.PaymentHandling handling = order.handlePaymentOutcome(outcome);
        if (handling == Order.PaymentHandling.IGNORED) {
            return;
        }
        if (handling == Order.PaymentHandling.COMPENSATION_REQUIRED) {
            compensatePaymentFailure(order);
            log.info("payment failed. orderId={}", order.getId());
            return;
        }
        if (outcome == Order.PaymentOutcome.SUCCESS) {
            removeOrderedCartItems(order);
            log.info("payment succeeded. orderId={}", order.getId());
            return;
        }
        log.warn("payment result is unknown. orderId={}", order.getId());
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
    private void removeOrderedCartItems(Order order) {
        // TODO(예정) : cart 도메인에 해당 로직 추가
        log.info("temporary ordered cart item cleanup assumed successful. orderId={}, userId={}, rewardIds={}",
                order.getId(), order.getUserId(), rewardIds(order));
    }

    private List<Long> rewardIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getRewardId)
                .toList();
    }
}
