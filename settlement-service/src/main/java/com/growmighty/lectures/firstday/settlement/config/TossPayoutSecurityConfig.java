package com.growmighty.lectures.firstday.settlement.config;

import com.growmighty.lectures.firstday.settlement.infrastructure.client.toss.TossPayoutJweCodec;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossPayoutProperties.class)
public class TossPayoutSecurityConfig {

    @Bean
    @ConditionalOnProperty(
            name = "settlement.toss-payout.enabled",
            havingValue = "true"
    )
    public TossPayoutJweCodec tossPayoutJweCodec(
            TossPayoutProperties properties,
            Clock clock
    ) {
        return new TossPayoutJweCodec(properties.securityKeyBytes(), clock);
    }
}
