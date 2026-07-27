package com.growmighty.lectures.firstday.project.project.application.port;

import java.math.BigDecimal;

/**
 * project-service가 order-service에게 묻는 것들 — 삭제 시 후원(주문) 이력 여부,
 * 그리고 push(PUT /internal/v1/projects/{id}/funded-amount)가 유실됐을 때 대비한
 * 모금액 확정 총액 pull 조회.
 */
public interface OrderPort {

    boolean hasOrderedReward(Long projectId);

    BigDecimal getFundedAmount(Long projectId);
}
