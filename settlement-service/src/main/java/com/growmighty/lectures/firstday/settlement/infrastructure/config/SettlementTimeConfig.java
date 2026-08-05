package com.growmighty.lectures.firstday.settlement.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SettlementTimeConfig {

    @Bean
    public Clock settlementClock() {
        return Clock.systemDefaultZone();
    }
}
