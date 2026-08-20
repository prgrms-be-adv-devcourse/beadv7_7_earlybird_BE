package com.growmighty.lectures.firstday.ai.policy.infrastructure.search;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * "policySearch" / "policyReindex" 서킷브레이커 전용 설정. TimeLimiterConfig(타임아웃)는
 * Resilience4JconfigBuilder 경로가 아니라 TimeLimiterRegistry.addConfiguration(id, ... )으로
 * 직접 등록해야 실제로 적용된다.
 */
@Configuration
@RequiredArgsConstructor
public class PolicySearchCircuitBreakerConfig {

    static final String POLICY_SEARCH_ID = "policySearch";
    static final String POLICY_REINDEX_ID = "policyReindex";

    private final TimeLimiterRegistry timeLimiterRegistry;

    @PostConstruct
    void registerPolicySearchTimeLimiterConfig() {
        timeLimiterRegistry.addConfiguration(POLICY_SEARCH_ID, TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .build());
        timeLimiterRegistry.addConfiguration(POLICY_REINDEX_ID, TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(30))
            .build());
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> policySearchCircuitBreakerCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(4)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .permittedNumberOfCallsInHalfOpenState(2)
                    .build()),
                POLICY_SEARCH_ID, POLICY_REINDEX_ID);
    }

}
