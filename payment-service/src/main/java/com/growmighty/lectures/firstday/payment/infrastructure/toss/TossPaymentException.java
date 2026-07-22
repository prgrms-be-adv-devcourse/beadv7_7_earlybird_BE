package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.ErrorCode;

public class TossPaymentException extends BusinessException {
    public TossPaymentException(
        ErrorCode errorCode,
        String tossCode,
        String tossMessage
    ) {
        super(errorCode,
            "토스 결제 승인에 실패했습니다. [" + tossCode + "] " + tossMessage);
    }
}
