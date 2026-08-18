package com.growmighty.lectures.firstday.file.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class FileCircuitBreakerConfig {

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
                // 존재하지 않는 리소스(404)는 원격 서비스 장애가 아니라 잘못된 입력이다 — 실패율에
                // 반영하면 잘못된 ID로 반복 요청될 때(버그/악의적 요청) 서킷이 열려 무관한 다른
                // 정상 요청까지 fail-closed로 거부될 수 있다.
                .ignoreExceptions(EntityNotFoundException.class)
                .build())
            .build());
    }
}
