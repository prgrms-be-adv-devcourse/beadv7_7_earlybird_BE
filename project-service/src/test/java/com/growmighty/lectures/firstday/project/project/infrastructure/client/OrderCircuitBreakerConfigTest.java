package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * order/file 서킷브레이커의 3초 타임아웃이 TimeLimiterRegistry에 실제로 등록되는지 검증한다 —
 * Resilience4JConfigBuilder.timeLimiterConfig(...)만으로는 적용되지 않는다는 게 이 클래스가
 * 고친 버그였다(ProjectSearchCircuitBreakerConfigTest와 같은 검증 방식, 클래스 설명 참고).
 */
class OrderCircuitBreakerConfigTest {

    @Test
    @DisplayName("order/file 이름으로 TimeLimiterRegistry에 3초 타임아웃 설정이 등록된다")
    void registersOrderAndFileTimeLimiterConfigs_withThreeSecondTimeout() {
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        new OrderCircuitBreakerConfig(timeLimiterRegistry).registerTimeLimiterConfigs();

        assertThat(timeLimiterRegistry.getConfiguration("order"))
                .isPresent()
                .get()
                .extracting(config -> config.getTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(timeLimiterRegistry.getConfiguration("file"))
                .isPresent()
                .get()
                .extracting(config -> config.getTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(3));
    }
}
