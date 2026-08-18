package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecoveryReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderPaymentRecoveryClientProperties.class)
public class OrderPaymentRecoveryClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder orderPaymentRecoveryLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient orderPaymentRecoveryRestClient(
            @Qualifier("orderPaymentRecoveryLoadBalancedRestClientBuilder") RestClient.Builder builder,
            OrderPaymentRecoveryClientProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public OrderPaymentRecoveryReader orderPaymentRecoveryReader(
            @Qualifier("orderPaymentRecoveryRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        return new OrderPaymentRecoveryHttpReader(restClient, objectMapper);
    }
}
