package com.growmighty.lectures.firstday.payment.application.exception;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

@Getter
public class PaymentGatewayException extends BusinessException {

    private final PaymentGatewayFailureType failureType;

    public PaymentGatewayException(@NonNull HttpStatus status, PaymentGatewayFailureType failureType, String message) {
        super(status, message);
        this.failureType = failureType;
    }
}
