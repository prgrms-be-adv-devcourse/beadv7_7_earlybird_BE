package com.growmighty.lectures.firstday.order.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OrderCircuitBreakerConfig {

    // 이 서비스에서 만들어지는 "모든" 차단기의 기본 근무 수칙.
    // 아래 숫자들은 수업 시간 안에 상태 변화를 눈으로 보기 위한 '교육용' 수치다.
    // (실무 기본값: 창 크기 100, 최소 호출 100, OPEN 대기 60초 — 훨씬 진득하게 지켜본다)
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
            // [초시계] 이 시간 안에 응답이 없으면 '실패'로 치고 스레드를 풀어준다.
            // 실험 ②의 "30초 인질극"을 3초 만에 끊어내는 장치.
            // ⚠️ 설정하지 않으면 기본값 1초 — 멀쩡한 호출도 실패 처리되는 함정 (자주 겪는 문제 ①)
            .timeLimiterConfig(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(3))
                .build())
            .circuitBreakerConfig(CircuitBreakerConfig.custom()
                .slidingWindowSize(10)                            // 최근 10번의 호출을 기억하는 창
                .minimumNumberOfCalls(4)                          // 최소 4번은 지켜본 뒤에야 판단한다 (표본이 1개인 통계는 통계가 아니다)
                .failureRateThreshold(50)                         // 실패율 50% 이상이면 차단기를 내린다 → OPEN
                .waitDurationInOpenState(Duration.ofSeconds(10))  // 내려간 채 10초 버틴 뒤 반쯤 열어본다 → HALF_OPEN
                .permittedNumberOfCallsInHalfOpenState(2)         // 시험 호출은 딱 2번만 통과시킨다
                .build())
            .build());
    }
}
