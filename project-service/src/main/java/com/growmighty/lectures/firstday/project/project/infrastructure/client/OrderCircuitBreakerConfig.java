package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * order/file 서킷브레이커("order", "file" id)의 타임아웃 설정. {@code Resilience4JConfigBuilder
 * .timeLimiterConfig(...)}는 spring-cloud-circuitbreaker-resilience4j에서 실제로 적용되지 않고
 * resilience4j 전역 기본값(1초)으로 떨어진다(ProjectSearchCircuitBreakerConfig 클래스 설명 참고,
 * 바이트코드 추적 + 실측으로 확인된 라이브러리 동작). 그래서 의도한 3초를 실제로 적용하려면
 * {@link TimeLimiterRegistry}에 "order"/"file" 이름으로 직접 등록해야 한다.
 */
@Configuration
@RequiredArgsConstructor
public class OrderCircuitBreakerConfig {

    private final TimeLimiterRegistry timeLimiterRegistry;

    /** TimeLimiterConfig(타임아웃)는 이 이름 붙은 설정 등록을 통해서만 실제로 적용된다(위 클래스 설명 참고). */
    @PostConstruct
    void registerTimeLimiterConfigs() {
        TimeLimiterConfig threeSecondTimeout = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(3))
            .build();
        timeLimiterRegistry.addConfiguration("order", threeSecondTimeout);
        timeLimiterRegistry.addConfiguration("file", threeSecondTimeout);
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build())
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build())
            .build());
    }
}
