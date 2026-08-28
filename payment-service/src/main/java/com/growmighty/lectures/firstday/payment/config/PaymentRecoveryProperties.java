package com.growmighty.lectures.firstday.payment.config;

import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.recovery")
@Validated
public record PaymentRecoveryProperties(

    @DefaultValue("PT3M")
    @DurationMin(seconds = 1)
    Duration confirmationTimeOut,

    @DefaultValue("100")
    @Positive
    Integer batchSize,

    @DefaultValue("PT10M")
    @DurationMin(seconds = 1)
    Duration maximumConfirmingDuration,

    @DefaultValue("PT5M")
    @DurationMin(seconds = 1)
    Duration readyTimeOut

) {
    public PaymentRecoveryProperties {
        if (maximumConfirmingDuration.compareTo(confirmationTimeOut) < 0) {
            throw new IllegalArgumentException(
                "maximumConfirmingDuration은 confirmationTimeOut보다 크거나 같아야 합니다."
            );
        }
    }
}
