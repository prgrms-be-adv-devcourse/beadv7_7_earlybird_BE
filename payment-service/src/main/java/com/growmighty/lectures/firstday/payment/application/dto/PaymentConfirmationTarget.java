package com.growmighty.lectures.firstday.payment.application.dto;

import java.math.BigDecimal;

/** 이미 DB에서 승인 권한을 얻은 결제라는 의미를 가진 DTO */
public record PaymentConfirmationTarget(
    Long paymentId,
    String pgOrderId,
    BigDecimal amount,
    String idempotencyKey
) {
}
