package com.growmighty.lectures.firstday.settlement.read;

/**
 * 정산 서비스가 읽기 위해 소유한 주문 상태 read-model.
 * order-service 의 OrderStatus 와 값은 같지만, 서비스가 분리된 지금은 별개의 타입이다.
 */
public enum OrderStatus {
    CREATED,
    PAID,
    CANCELLED
}
