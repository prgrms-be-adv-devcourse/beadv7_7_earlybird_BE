package com.growmighty.lectures.firstday.refund.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.refund-recovery")
@Validated
public record RefundRecoveryProperties(
    @DefaultValue("PT3M")
    @DurationMin(seconds = 1)
    Duration requestedTimeOut,

    @DefaultValue("100")
    @Positive
    Integer batchSize,

    @DefaultValue("3")
    @PositiveOrZero
    Integer maximumRetryCount,

    @DefaultValue("PT5M")
    @DurationMin(seconds = 1)
    Duration retryDelay
    ) {
}
