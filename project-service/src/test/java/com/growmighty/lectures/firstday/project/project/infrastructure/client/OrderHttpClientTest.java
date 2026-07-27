package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 실제 Resilience4j 서킷브레이커 대신 CircuitBreaker.run(...)을 "그대로 실행"하도록 스텁해서,
 * OrderHttpClient가 성공/실패 케이스에서 어떤 값을 반환·던지는지만 검증한다 (차단기 자체의
 * open/half-open 전이 로직은 OrderCircuitBreakerConfig 설정값 검증 대상이라 여기선 다루지 않는다).
 */
class OrderHttpClientTest {

    private final OrderFeignClient orderFeignClient = mock(OrderFeignClient.class);
    private final CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final OrderHttpClient orderHttpClient = new OrderHttpClient(orderFeignClient, circuitBreakerFactory);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(circuitBreakerFactory.create("order")).thenReturn(circuitBreaker);
        when(circuitBreaker.run(any(Supplier.class), any(Function.class))).thenAnswer(invocation -> {
            Supplier<Object> toRun = invocation.getArgument(0);
            Function<Throwable, Object> fallback = invocation.getArgument(1);
            try {
                return toRun.get();
            } catch (Throwable t) {
                return fallback.apply(t);
            }
        });
    }

    @Test
    @DisplayName("order-service 호출이 성공하면 응답의 data를 그대로 반환한다")
    void hasOrderedReward_success() {
        when(orderFeignClient.hasOrderedReward(1L))
                .thenReturn(ApiResponse.ok(true));

        boolean result = orderHttpClient.hasOrderedReward(1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("order-service 호출이 실패하면 조용히 넘기지 않고 503으로 변환한다 (fail-closed)")
    void hasOrderedReward_failure_throwsServiceUnavailable() {
        when(orderFeignClient.hasOrderedReward(1L)).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> orderHttpClient.hasOrderedReward(1L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
