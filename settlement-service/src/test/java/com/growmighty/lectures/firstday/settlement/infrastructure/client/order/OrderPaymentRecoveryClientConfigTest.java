package com.growmighty.lectures.firstday.settlement.infrastructure.client.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.settlement.application.port.order.OrderPaymentRecoveryReader;
import com.growmighty.lectures.firstday.settlement.infrastructure.client.SettlementRestClientConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class OrderPaymentRecoveryClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SettlementRestClientConfig.class,
                    OrderPaymentRecoveryClientConfig.class,
                    ObjectMapperConfig.class
            );

    @Test
    @DisplayName("Order 복구 HTTP reader 하나와 기존 연결·응답 제한시간을 구성한다")
    void configuresRecoveryReader() {
        contextRunner
                .withPropertyValues(
                        "settlement.project-order.http.base-url=http://order-service",
                        "settlement.project-order.http.connect-timeout=250ms",
                        "settlement.project-order.http.read-timeout=750ms"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OrderPaymentRecoveryReader.class);
                    assertThat(context.getBean(OrderPaymentRecoveryReader.class))
                            .isInstanceOf(OrderPaymentRecoveryHttpReader.class);
                    assertThat(context).hasBean("orderPaymentRecoveryRestClient");
                    assertThat(context.getBean("orderPaymentRecoveryRestClient")).isInstanceOf(RestClient.class);
                    assertThat(context.getBeanFactory().findAnnotationOnBean(
                            "loadBalancedRestClientBuilder", LoadBalanced.class
                    )).isNotNull();

                    OrderPaymentRecoveryClientProperties properties = context.getBean(
                            OrderPaymentRecoveryClientProperties.class
                    );
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(250));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(750));
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
