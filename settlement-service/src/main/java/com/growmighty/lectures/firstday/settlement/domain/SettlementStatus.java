package com.growmighty.lectures.firstday.settlement.domain;

public enum SettlementStatus {
    /** 정산 대상으로 잡혔으나 아직 확정 전 */
    PENDING,
    /** 정산 확정 완료 */
    COMPLETED,
    /** 정산 처리 실패 */
    FAILED
}
