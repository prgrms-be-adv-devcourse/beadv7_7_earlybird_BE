package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DummyTossSellerRegistrationProperties.class)
public class DummyTossSellerRegistrationConfig {

    @Bean
    public TossSellerRegistrationGateway tossSellerRegistrationGateway(
            DummyTossSellerRegistrationProperties properties
    ) {
        return new DummyTossSellerRegistrationGateway(properties.scenario());
    }
}
