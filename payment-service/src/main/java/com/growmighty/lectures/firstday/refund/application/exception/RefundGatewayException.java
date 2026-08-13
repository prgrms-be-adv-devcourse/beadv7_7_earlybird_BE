package com.growmighty.lectures.firstday.refund.application.exception;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RefundGatewayException extends BusinessException {
    private final RefundGatewayFailureType failureType;

    public RefundGatewayException(
        HttpStatus status,
        RefundGatewayFailureType failureType,
        String message
    ) {
        super(status, message);
        this.failureType = failureType;
    }
}
