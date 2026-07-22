package com.growmighty.lectures.firstday.payment.application.exception;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.ErrorCode;

public class PaymentConfirmationInProgressException extends BusinessException {
    public PaymentConfirmationInProgressException(String pgOrderId) {
        super(
            ErrorCode.INVALID_STATE,
            "이미 다른 요청에서 결제 승인을 처리하고 있습니다. pgOrderId = " + pgOrderId
        );
    }
}
