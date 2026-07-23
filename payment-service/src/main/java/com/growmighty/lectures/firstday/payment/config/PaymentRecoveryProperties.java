package com.growmighty.lectures.firstday.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.recovery")
public record PaymentRecoveryProperties(
    Duration confirmationTimeOut,
    Integer batchSize
) {

    /**
     * TimeOut 시간은 3분으로 결정했습니다.
     * @param confirmationTimeOut
     * @param batchSize
     */
    public PaymentRecoveryProperties {
        confirmationTimeOut = confirmationTimeOut == null
            ? Duration.ofMinutes(3)
            : confirmationTimeOut;

        batchSize = batchSize == null
            ? 100
            : batchSize;


        if (confirmationTimeOut.isZero() || confirmationTimeOut.isNegative()) {
            throw new IllegalArgumentException("결제 승인 타임아웃은 0보다 커야 합니다.");
        }

        if (batchSize <= 0) {
            throw new IllegalArgumentException("결제 복구 배치 크기는 0보다 커야합니다.");
        }
    }
}
