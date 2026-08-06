package com.growmighty.lectures.firstday.payment.config;

import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayException;
import com.growmighty.lectures.firstday.payment.application.exception.PaymentGatewayFailureType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentApprovalResilienceConfigTest {

    private Retry paymentApprovalRetry;
    private CircuitBreaker paymentApprovalCircuitBreaker;

    @BeforeEach
    void setUp() {
        PaymentApprovalResilienceConfig config = new PaymentApprovalResilienceConfig();
        paymentApprovalRetry = config.paymentApprovalRetry();
        paymentApprovalCircuitBreaker = config.paymentApprovalCircuitBreaker();
    }

    // 추가 : 결과 미확정 오류의 최대 재시도 횟수 검증
    @Test
    void 결과_미확정_오류는_총_세번_시도한다() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<Void> supplier = Retry.decorateSupplier(paymentApprovalRetry, () -> {
            attempts.incrementAndGet();
            throw gatewayException(PaymentGatewayFailureType.UNCERTAIN);
        });

        assertThatThrownBy(supplier::get)
            .isInstanceOf(PaymentGatewayException.class);

        assertThat(attempts).hasValue(3);
    }

    // 추가 : 확정 실패 오류는 재시도하지 않음을 검증
    @Test
    void 확정_실패_오류는_재시도하지_않는다() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<Void> supplier = Retry.decorateSupplier(paymentApprovalRetry, () -> {
            attempts.incrementAndGet();
            throw gatewayException(PaymentGatewayFailureType.DEFINITIVE);
        });

        assertThatThrownBy(supplier::get)
            .isInstanceOf(PaymentGatewayException.class);

        assertThat(attempts).hasValue(1);
    }

    // 추가 : 결과 미확정 오류 누적 시 CircuitBreaker 차단 검증
    @Test
    void 결과_미확정_오류가_다섯번_누적되면_CircuitBreaker가_열린다() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Void> supplier = CircuitBreaker.decorateSupplier(
            paymentApprovalCircuitBreaker,
            () -> {
                calls.incrementAndGet();
                throw gatewayException(PaymentGatewayFailureType.UNCERTAIN);
            }
        );

        for (int index = 0; index < 5; index++) {
            assertThatThrownBy(supplier::get)
                .isInstanceOf(PaymentGatewayException.class);
        }

        assertThat(paymentApprovalCircuitBreaker.getState())
            .isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(supplier::get)
            .isInstanceOf(CallNotPermittedException.class);
        assertThat(calls).hasValue(5);
    }

    private PaymentGatewayException gatewayException(PaymentGatewayFailureType failureType) {
        return new PaymentGatewayException(
            HttpStatus.SERVICE_UNAVAILABLE,
            failureType,
            "Toss 오류"
        );
    }
}
