package com.growmighty.lectures.firstday.board.infrastructure.client.order;

import com.growmighty.lectures.firstday.board.infrastructure.client.order.dto.OrderPurchaseVerificationApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// order-service 팀에 요청한 계약 — 이 엔드포인트는 order-service 쪽에 아직 구현돼 있지 않다 (board-service 몫만 먼저 작성).
// 판정 기준: userId의 주문 중 rewardId를 포함한 주문 아이템이 있고, 그 주문의 "현재" status가 PAID인 것이 하나라도 있으면 verified=true.
// (주문 상태는 현재값 하나만 유지되므로, 결제 후 취소된 주문은 현재 상태가 PAID가 아니라 자동으로 제외된다)
@FeignClient(name = "order-service")
public interface OrderFeignClient {

    @GetMapping("/internal/v1/orders/purchase-verification")
    ApiResponse<OrderPurchaseVerificationApiData> verifyPurchase(
        @RequestParam("userId") Long userId, @RequestParam("rewardId") Long rewardId);
}