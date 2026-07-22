package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "payment.gateway", havingValue = "toss")
public class TossPaymentConfig {

    @Bean
    public RestClient tossRestClient(
        @Value("${payment.toss.secret-key}") String secretKey
    ) {
        return RestClient.builder()
            .baseUrl("https://api.tosspayments.com")
            .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
            .build();
    }
}
