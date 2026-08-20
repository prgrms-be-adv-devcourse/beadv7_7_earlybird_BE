package com.growmighty.lectures.firstday.user.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "user.security")
@Validated
public record UserSecurityProperties(
    @NotBlank String encryptionKey
) {
}
