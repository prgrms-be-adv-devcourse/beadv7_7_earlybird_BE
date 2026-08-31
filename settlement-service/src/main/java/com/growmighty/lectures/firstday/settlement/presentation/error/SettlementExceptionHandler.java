// TODO(settlement-plan): Map REVIEW_REQUIRED and input-contract failures consistently without transport-specific branches.
package com.growmighty.lectures.firstday.settlement.presentation.error;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode;
import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.growmighty.lectures.firstday.settlement")
public class SettlementExceptionHandler {

    @ExceptionHandler(SettlementException.class)
    public ResponseEntity<ApiResponse<Void>> handleSettlementException(SettlementException exception) {
        SettlementErrorCode errorCode = exception.errorCode();
        HttpStatus status = statusOf(errorCode);

        if (status.is5xxServerError()) {
            log.error("[{}] {}", errorCode.getCode(), exception.getMessage(), exception);
        } else {
            log.warn("[{}] {}", errorCode.getCode(), exception.getMessage(), exception);
        }

        ApiResponse.ApiError error = CommonApiErrorAdapter.withoutCode(errorCode.getMessage());
        return ResponseEntity.status(status).body(ApiResponse.fail(error));
    }

    private HttpStatus statusOf(SettlementErrorCode errorCode) {
        return switch (errorCode) {
            case PAYOUT_PROFILE_NOT_READY,
                    CREATOR_INFORMATION_INVALID,
                    SELLER_REGISTRATION_REJECTED -> HttpStatus.CONFLICT;
            case PROJECT_SETTLEMENT_NOT_FOUND,
                    PROJECT_REFUND_REQUEST_NOT_FOUND,
                    PROJECT_RECONCILIATION_REVIEW_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ORDER_PAYMENT_INPUTS_UNAVAILABLE,
                    PROJECT_SETTLEMENT_TARGETS_UNAVAILABLE,
                    CREATOR_INFORMATION_UNAVAILABLE,
                    SELLER_REGISTRATION_RESULT_UNKNOWN -> HttpStatus.SERVICE_UNAVAILABLE;
            case SETTLEMENT_DATA_INCONSISTENT -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
