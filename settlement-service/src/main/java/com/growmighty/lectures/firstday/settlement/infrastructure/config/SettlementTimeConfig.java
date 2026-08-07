// TODO(settlement-plan): Expose one Asia/Seoul Clock used by scheduler, refund due dates, runs, and deterministic tests.
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
