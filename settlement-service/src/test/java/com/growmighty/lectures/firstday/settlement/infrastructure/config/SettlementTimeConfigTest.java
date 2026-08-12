package com.growmighty.lectures.firstday.settlement.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SettlementTimeConfigTest {

    @Test
    void providesSeoulClock() {
        assertThat(new SettlementTimeConfig().settlementClock().getZone())
                .isEqualTo(ZoneId.of("Asia/Seoul"));
    }
}
