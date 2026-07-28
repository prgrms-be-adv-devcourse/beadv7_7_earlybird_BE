package com.growmighty.lectures.firstday.order.domain;

public class DuplicateOrderCreationException extends RuntimeException {
    private final Long userId;
    private final String idempotencyKey;

    public DuplicateOrderCreationException(Long userId, String idempotencyKey, Throwable cause) {
        super("Duplicate order creation request. userId=" + userId + ", idempotencyKey=" + idempotencyKey, cause);
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getUserId() {
        return userId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
