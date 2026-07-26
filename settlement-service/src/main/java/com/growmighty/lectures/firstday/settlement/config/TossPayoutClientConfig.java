package com.growmighty.lectures.firstday.settlement.config;

import com.growmighty.lectures.firstday.settlement.application.port.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.toss.TossPayoutGateway;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.toss.TossPayoutJweCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossPayoutProperties.class)
public class TossPayoutClientConfig {

    @Bean
    @ConditionalOnProperty(
            name = "settlement.toss-payout.enabled",
            havingValue = "true"
    )
    public RestClient tossPayoutRestClient(TossPayoutProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @ConditionalOnProperty(
            name = "settlement.toss-payout.enabled",
            havingValue = "true"
    )
    public PayoutGateway payoutGateway(
            @Qualifier("tossPayoutRestClient") RestClient restClient,
            TossPayoutJweCodec jweCodec,
            ObjectMapper objectMapper,
            TossPayoutProperties properties
    ) {
        return new TossPayoutGateway(
                restClient,
                jweCodec,
                objectMapper,
                properties.secretKey()
        );
    }
}
