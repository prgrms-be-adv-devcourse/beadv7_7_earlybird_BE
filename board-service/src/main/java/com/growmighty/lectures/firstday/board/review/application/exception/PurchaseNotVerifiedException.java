package com.growmighty.lectures.firstday.board.review.application.exception;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * order-service가 정상 응답했지만 해당 리워드에 대한 결제 완료 주문을 확인하지 못했을 때 던진다.
 * (order-service 자체와 통신이 안 되는 경우와는 다른 사유 — 그건 ServiceUnavailableException(503))
 */
public class PurchaseNotVerifiedException extends BusinessException {
    public PurchaseNotVerifiedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}