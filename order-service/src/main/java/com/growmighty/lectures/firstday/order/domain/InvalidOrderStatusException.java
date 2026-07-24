package com.growmighty.lectures.firstday.order.domain;

public class InvalidOrderStatusException extends IllegalStateException {
    public InvalidOrderStatusException(OrderStatus currentStatus, OrderStatus nextStatus) {
        super("Invalid order status transition. currentStatus=" + currentStatus + ", nextStatus=" + nextStatus);
    }
}
