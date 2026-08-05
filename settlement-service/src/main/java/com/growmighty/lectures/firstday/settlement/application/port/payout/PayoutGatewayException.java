package com.growmighty.lectures.firstday.settlement.application.port.payout;

public final class PayoutGatewayException extends RuntimeException {

    public PayoutGatewayException(String message) {
        super(message);
    }

    public PayoutGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
