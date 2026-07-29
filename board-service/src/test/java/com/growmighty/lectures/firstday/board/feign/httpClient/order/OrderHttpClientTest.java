package com.growmighty.lectures.firstday.board.feign.httpClient.order;

import com.growmighty.lectures.firstday.board.feign.port.dto.PurchaseVerification;
import com.growmighty.lectures.firstday.board.feign.httpClient.order.dto.OrderPurchaseVerificationApiData;
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

// UserHttpClientTest와 같은 원칙: Feign 통신 자체는 이미 검증된 프레임워크 기능이므로 mock으로 끊고,
// 우리가 새로 짠 서킷브레이커 배선(성공 매핑 / 통신 실패 시 하드 실패)만 실제 CircuitBreakerFactory로 좁게 검증한다.
// order-service가 정상 응답했지만 verified=false인 케이스는 장애가 아니므로 여기서 다루지 않는다 — 그 판단은 ReviewService의 몫(ReviewServiceTest 참고).
@ExtendWith(MockitoExtension.class)
class OrderHttpClientTest {

    @Mock
    private OrderFeignClient orderFeignClient;

    private OrderHttpClient orderHttpClient;

    @BeforeEach
    void setUp() {
        Resilience4JCircuitBreakerFactory circuitBreakerFactory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null);
        orderHttpClient = new OrderHttpClient(orderFeignClient, circuitBreakerFactory);
    }

    @Test
    @DisplayName("정상 응답이면 PurchaseVerification으로 변환해 반환한다")
    void verifyPurchase_success() {
        when(orderFeignClient.verifyPurchase(1L, 100L))
            .thenReturn(ApiResponse.ok(new OrderPurchaseVerificationApiData(true, "얼리버드 리워드")));

        PurchaseVerification result = orderHttpClient.verifyPurchase(1L, 100L);

        assertThat(result).isEqualTo(new PurchaseVerification(true, "얼리버드 리워드"));
    }

    @Test
    @DisplayName("verified=false 응답은 장애가 아니라 그대로 반환한다")
    void verifyPurchase_notVerified_returnsAsIs() {
        when(orderFeignClient.verifyPurchase(1L, 100L))
            .thenReturn(ApiResponse.ok(new OrderPurchaseVerificationApiData(false, null)));

        PurchaseVerification result = orderHttpClient.verifyPurchase(1L, 100L);

        assertThat(result).isEqualTo(new PurchaseVerification(false, null));
    }

    @Test
    @DisplayName("Feign 호출이 실패하면 낙관적으로 넘어가지 않고 ServiceUnavailableException으로 하드 실패한다")
    void verifyPurchase_feignFailure_failsHardViaFallback() {
        when(orderFeignClient.verifyPurchase(1L, 100L)).thenThrow(new RuntimeException("order-service 응답 없음"));

        assertThatThrownBy(() -> orderHttpClient.verifyPurchase(1L, 100L))
            .isInstanceOf(ServiceUnavailableException.class);
    }
}