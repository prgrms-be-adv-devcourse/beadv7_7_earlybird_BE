package com.growmighty.lectures.firstday.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "earlybird.seed")
public record SeedUserProperties(Account buyer, Account seller, Account admin) {

    public record Account(String email, String password, String name, String phone) {
    }
}
