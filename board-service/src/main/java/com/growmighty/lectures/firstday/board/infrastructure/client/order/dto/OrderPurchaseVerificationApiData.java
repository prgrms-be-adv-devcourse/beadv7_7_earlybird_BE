package com.growmighty.lectures.firstday.board.infrastructure.client.order.dto;

/** order-service의 GET /internal/v1/orders/purchase-verification 응답 data 부분. */
public record OrderPurchaseVerificationApiData(boolean verified, String rewardName) {
}