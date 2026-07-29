package com.growmighty.lectures.firstday.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 값은 코드가 아니라 config-server(private 레포)에만 둔다 — DB 비밀번호 등 다른 민감값과 같은 경계를 쓴다. .env로 옮기면 이미 있는 config-server 배달 경로 대신 배관을 하나 더 만드는 셈이라 채택하지 않았다. */
@ConfigurationProperties(prefix = "earlybird.seed")
public record SeedUserProperties(Account buyer, Account seller, Account admin) {

    public record Account(String email, String password, String name, String phone) {
    }
}
