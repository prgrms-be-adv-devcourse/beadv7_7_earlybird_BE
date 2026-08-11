package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.port.RewardPort;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class OrderStockHandler {

    private final RewardPort rewardPort;
    private final OrderRemoteCallExecutor remoteCalls;

    // 재고 확보 작업 로직 -> 일단 차감 후 다음 단계에서 복원
    // '재고 확보'를 '감소 처리'로 하는 것 자체가 확정은 아님
    // 경우에 따라서 reward 도메인이랑 협의 필요
    void reserveStock(Order order, List<OrderItem> confirmedItems) {
        for (OrderItem item : order.getItems()) {
            remoteCalls.execute("reward-decrease-stock",
                    () -> rewardPort.decreaseStock(item.getRewardId(), item.getQuantity(), item.getOrder().getId()));
            item.markStockReserved();
            confirmedItems.add(item);
        }
    }

    // 재고 복원
    void releaseStock(Order order) {
        // TODO(미정) : 정합성 검증 추가?
        for (OrderItem item : order.getItems()) {
            if (item.isStockReserved()) {
                restoreStock(item);
            }
        }
    }

    void compensateStockFailure(Order order, List<OrderItem> confirmedItems) {
        try {
            for (OrderItem item : confirmedItems) {
                restoreStock(item);
            }
            order.markStockReservationFailed();
        } catch (RuntimeException compensationFailure) {
            order.markStockCompensationPending();
        }
    }

    private void restoreStock(OrderItem item) {
        remoteCalls.execute("reward-restore-stock",
                () -> rewardPort.restoreStock(item.getRewardId(), item.getQuantity(), item.getOrder().getId()));
        item.markStockRestored();
    }
}
