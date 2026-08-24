package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.project;

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

@Configuration
@RequiredArgsConstructor
public class ProjectSearchCircuitBreakerConfig {

    static final String PROJECTS_ID = "projects";

    private final TimeLimiterRegistry timeLimiterRegistry;

    @PostConstruct
    void registerProjectsTimeLimiterConfig() {
        timeLimiterRegistry.addConfiguration(PROJECTS_ID, TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(5))
            .build()
        );
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> projectsCircuitBreakerCustomizer() {
        return factory -> factory.configure(builder -> builder
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build()),
            PROJECTS_ID
        );
    }
}
