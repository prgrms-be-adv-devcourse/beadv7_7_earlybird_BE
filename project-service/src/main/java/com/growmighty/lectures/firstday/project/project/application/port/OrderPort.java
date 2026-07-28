package com.growmighty.lectures.firstday.project.project.application.port;

import java.math.BigDecimal;

/**
 * 프로젝트 삭제 시 order-service에게 "이 프로젝트에 후원(주문) 이력이 있는지" 묻는 계약,
 * 그리고 1분마다 "지금 확정 누적 총액이 얼마인지" pull 조회하는 계약.
 */
public interface OrderPort {

    boolean hasOrderedReward(Long projectId);

    /** 무후원 프로젝트는 0을 반환한다(음수 없음). order-service 장애 시 예외를 던진다(fail-closed). */
    BigDecimal getFundedAmount(Long projectId);
}
