package com.growmighty.lectures.firstday.refund.application.exception;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

public class RefundGatewayException extends BusinessException {
    public RefundGatewayException(@NonNull HttpStatus status, String message) {
        super(status, message);
    }
}
