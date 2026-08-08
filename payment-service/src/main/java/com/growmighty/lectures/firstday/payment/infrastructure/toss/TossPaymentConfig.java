package com.growmighty.lectures.firstday.payment.infrastructure.toss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "payment.gateway", havingValue = "toss")
public class TossPaymentConfig {

    @Bean
    public RestClient tossRestClient(
        @Value("${payment.toss.secret-key}") String secretKey
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl("https://api.tosspayments.com")
            .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
            .build();
    }
}
