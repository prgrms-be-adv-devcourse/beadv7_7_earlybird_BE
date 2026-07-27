package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

public final class TossPayoutSecurityException extends RuntimeException {

    public TossPayoutSecurityException(String message) {
        super(message);
    }

    public TossPayoutSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
