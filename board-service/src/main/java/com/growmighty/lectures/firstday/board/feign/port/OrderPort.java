package com.growmighty.lectures.firstday.board.feign.port;

import com.growmighty.lectures.firstday.board.feign.port.dto.PurchaseVerification;

/**
 * board-service 는 order-service 의 클래스를 알지 못한다. 오직 이 인터페이스로만 구매 여부를 바라보고,
 * 실제 통신은 infrastructure 의 HTTP 클라이언트가 담당한다.
 */
public interface OrderPort {
    PurchaseVerification verifyPurchase(Long userId, Long rewardId);
}