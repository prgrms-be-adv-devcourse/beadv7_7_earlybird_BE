package com.growmighty.lectures.firstday.settlement.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.order.OrderPaymentRecoveryClientConfig;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.user.UserCreatorInformationClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class SettlementRestClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SettlementRestClientConfig.class,
                    OrderPaymentRecoveryClientConfig.class,
                    UserCreatorInformationClientConfig.class,
                    ObjectMapperConfig.class
            );

    @Test
    void configuresOneUnqualifiedBuilderForEureka() {
        contextRunner.withPropertyValues(
                        "settlement.project-order.http.base-url=http://order-service",
                        "settlement.creator-information.http.base-url=http://user-service"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(RestClient.Builder.class)).hasSize(2);
                    assertThat(context.getBean(RestClient.Builder.class))
                            .isSameAs(context.getBean("plainRestClientBuilder"));
                    assertThat(context.getBeanFactory().findAnnotationOnBean(
                            "loadBalancedRestClientBuilder", LoadBalanced.class
                    )).isNotNull();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
