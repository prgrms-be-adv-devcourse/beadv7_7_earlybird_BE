package com.growmighty.lectures.firstday.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "payment.security")
@Validated
public record PaymentSecurityProperties(
    @NotBlank String encryptionKey
) {
}
