package com.growmighty.lectures.firstday.order.domain;

public enum OrderStatus {
    CREATED,
    STOCK_FAILED,
    PAYMENT_REQUEST,
    PAYMENT_PROCESSING,
    PAYMENT_FAILED,
    PAID,
    CANCELLED
}
