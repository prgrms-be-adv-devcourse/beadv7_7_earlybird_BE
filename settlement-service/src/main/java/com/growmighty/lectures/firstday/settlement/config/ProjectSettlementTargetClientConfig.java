package com.growmighty.lectures.firstday.settlement.config;

import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.project.ProjectSettlementTargetHttpReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "settlement.project-target.mode",
        havingValue = "http",
        matchIfMissing = true
)
@EnableConfigurationProperties(ProjectSettlementTargetClientProperties.class)
public class ProjectSettlementTargetClientConfig {

    @Bean
    @Primary
    public RestClient.Builder projectSettlementTargetPlainRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder projectSettlementTargetLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient projectSettlementTargetRestClient(
            @LoadBalanced RestClient.Builder builder,
            ProjectSettlementTargetClientProperties properties
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
    public ProjectSettlementTargetReader projectSettlementTargetReader(
            @Qualifier("projectSettlementTargetRestClient") RestClient restClient
    ) {
        return new ProjectSettlementTargetHttpReader(restClient);
    }
}
