package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

public class TossPaymentException extends BusinessException {
    public TossPaymentException(
        HttpStatus status,
        String tossCode,
        String tossMessage
    ) {
        super(status,
            "토스 결제 승인에 실패했습니다. [" + tossCode + "] " + tossMessage);
    }
}
