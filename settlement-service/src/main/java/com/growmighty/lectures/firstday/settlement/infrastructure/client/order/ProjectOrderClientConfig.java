// TODO(settlement-plan): Keep only the Feign recovery adapter wiring and remove normal-run HTTP selection after Kafka migration.
package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import com.growmighty.lectures.firstday.settlement.application.port.order.ProjectOrderReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.order.ProjectOrderHttpReader;
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
        name = "settlement.project-order.mode",
        havingValue = "http",
        matchIfMissing = true
)
@EnableConfigurationProperties(ProjectOrderClientProperties.class)
public class ProjectOrderClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder projectOrderLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient projectOrderRestClient(
            @Qualifier("projectOrderLoadBalancedRestClientBuilder") RestClient.Builder builder,
            ProjectOrderClientProperties properties
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
    public ProjectOrderReader projectOrderReader(
            @Qualifier("projectOrderRestClient") RestClient restClient
    ) {
        return new ProjectOrderHttpReader(restClient);
    }
}
