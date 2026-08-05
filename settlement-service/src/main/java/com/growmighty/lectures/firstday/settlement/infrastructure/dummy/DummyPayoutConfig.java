package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.payout.PayoutGateway;
import com.growmighty.lectures.firstday.settlement.infrastructure.dummy.DummyPayoutGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DummyPayoutProperties.class)
public class DummyPayoutConfig {

    @Bean
    public PayoutGateway payoutGateway(DummyPayoutProperties properties) {
        return new DummyPayoutGateway(properties.scenario());
    }
}
