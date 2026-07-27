package com.growmighty.lectures.firstday.board.infrastructure.client.user;

import com.growmighty.lectures.firstday.board.application.port.dto.UserSnapshot;
import com.growmighty.lectures.firstday.board.infrastructure.client.user.dto.UserApiData;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 실제 HTTP 트래픽(Feign이 어떻게 통신하는지)은 검증 대상이 아니다 — OpenFeign 자체는 이미 검증된 프레임워크 기능이다.
// 여기서 새로 짠 부분, 즉 UserFeignClient 호출을 서킷브레이커로 감싼 배선과 실패 시 하드 실패(fallback) 동작만 좁게 검증한다.
@ExtendWith(MockitoExtension.class)
class UserHttpClientTest {

    @Mock
    private UserFeignClient userFeignClient;

    private UserHttpClient userHttpClient;

    @BeforeEach
    void setUp() {
        // 실제 리포지스트리를 쓰는 진짜 CircuitBreakerFactory. OPEN 상태 전환까지는 이번엔 검증하지 않으므로
        // 기본 설정(BoardCircuitBreakerConfig 커스터마이저 미적용)으로도 성공/실패 단건 동작을 확인하기엔 충분하다.
        Resilience4JCircuitBreakerFactory circuitBreakerFactory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null);
        userHttpClient = new UserHttpClient(userFeignClient, circuitBreakerFactory);
    }

    @Test
    @DisplayName("정상 응답이면 UserSnapshot으로 변환해 반환한다")
    void getUser_success() {
        when(userFeignClient.fetchUser(1L)).thenReturn(ApiResponse.ok(new UserApiData(1L, "홍길동")));

        UserSnapshot snapshot = userHttpClient.getUser(1L);

        assertThat(snapshot).isEqualTo(new UserSnapshot(1L, "홍길동"));
    }

    @Test
    @DisplayName("Feign 호출이 실패하면 낙관적으로 넘어가지 않고 ServiceUnavailableException으로 하드 실패한다")
    void getUser_feignFailure_failsHardViaFallback() {
        when(userFeignClient.fetchUser(1L)).thenThrow(new RuntimeException("user-service 응답 없음"));

        assertThatThrownBy(() -> userHttpClient.getUser(1L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}