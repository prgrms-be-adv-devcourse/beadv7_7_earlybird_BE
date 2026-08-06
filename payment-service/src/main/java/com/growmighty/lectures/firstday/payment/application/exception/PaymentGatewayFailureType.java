package com.growmighty.lectures.firstday.payment.application.exception;

/**
 * DEF : PG가 승인 거절 확정 응답
 * UNC : 타임아웃, 네트워크, 5xx, 알 수 없는 응답
 */
public enum PaymentGatewayFailureType {
    DEFINITIVE,
    UNCERTAIN
}
