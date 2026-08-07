// TODO(settlement-plan): Keep one application error type while preserving causes from Kafka, reconciliation, and payout adapters.
package com.growmighty.lectures.firstday.settlement.application.error;

public final class SettlementException extends RuntimeException {

    private final SettlementErrorCode errorCode;

    public SettlementException(SettlementErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SettlementException(SettlementErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public SettlementErrorCode errorCode() {
        return errorCode;
    }
}
