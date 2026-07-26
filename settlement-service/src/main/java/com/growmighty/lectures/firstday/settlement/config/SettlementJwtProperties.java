package com.growmighty.lectures.firstday.settlement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record SettlementJwtProperties(String secret) {
}
