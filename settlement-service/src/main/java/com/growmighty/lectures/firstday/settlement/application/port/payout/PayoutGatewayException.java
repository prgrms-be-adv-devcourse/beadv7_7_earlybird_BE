// TODO(settlement-plan): Distinguish retryable, final, and unknown payout results without transport-specific exceptions above the adapter.
package com.growmighty.lectures.firstday.settlement.application.port.payout;

public final class PayoutGatewayException extends RuntimeException {

    public PayoutGatewayException(String message) {
        super(message);
    }

    public PayoutGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
