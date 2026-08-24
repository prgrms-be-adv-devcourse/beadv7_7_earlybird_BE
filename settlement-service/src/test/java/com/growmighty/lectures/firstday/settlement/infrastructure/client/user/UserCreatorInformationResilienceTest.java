package com.growmighty.lectures.firstday.settlement.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException.FailureType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserCreatorInformationResilienceTest {
    private Retry retry;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        UserCreatorInformationClientConfig config = new UserCreatorInformationClientConfig();
        retry = config.creatorInformationRetry();
        circuitBreaker = config.creatorInformationCircuitBreaker();
    }

    @Test
    void retriesAvailabilityFailureOnce() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<Void> supplier = Retry.decorateSupplier(retry, () -> { attempts.incrementAndGet(); throw availabilityFailure(); });
        assertThatThrownBy(supplier::get).isInstanceOf(CreatorInformationException.class);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void opensOnlyForAvailabilityFailures() {
        Supplier<Void> supplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> { throw availabilityFailure(); });
        for (int index = 0; index < 5; index++) {
            assertThatThrownBy(supplier::get).isInstanceOf(CreatorInformationException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(supplier::get).isInstanceOf(CallNotPermittedException.class);
    }

    private static CreatorInformationException availabilityFailure() {
        return new CreatorInformationException(FailureType.AVAILABILITY, "User를 사용할 수 없습니다.", null);
    }
}
