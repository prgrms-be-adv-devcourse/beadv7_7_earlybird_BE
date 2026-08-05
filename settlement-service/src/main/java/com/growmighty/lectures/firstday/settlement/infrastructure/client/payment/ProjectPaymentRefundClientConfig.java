package com.growmighty.lectures.firstday.settlement.infrastructure.client.payment;

import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationGateway;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.payment.ProjectPaymentRefundHttpGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "settlement.payment-cancellation.mode",
        havingValue = "http",
        matchIfMissing = true
)
@EnableConfigurationProperties(ProjectPaymentRefundClientProperties.class)
public class ProjectPaymentRefundClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder projectPaymentRefundLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient projectPaymentRefundRestClient(
            @Qualifier("projectPaymentRefundLoadBalancedRestClientBuilder")
            RestClient.Builder builder,
            ProjectPaymentRefundClientProperties properties
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
    public ProjectPaymentCancellationGateway projectPaymentCancellationGateway(
            @Qualifier("projectPaymentRefundRestClient") RestClient restClient
    ) {
        return new ProjectPaymentRefundHttpGateway(restClient);
    }
}
