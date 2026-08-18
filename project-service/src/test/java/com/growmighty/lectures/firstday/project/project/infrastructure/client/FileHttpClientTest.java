package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileHttpClientTest {

    private final FileFeignClient fileFeignClient = mock(FileFeignClient.class);
    private final CircuitBreakerFactory circuitBreakerFactory = mock(CircuitBreakerFactory.class);
    private final CircuitBreaker circuitBreaker = mock(CircuitBreaker.class);
    private final FileHttpClient fileHttpClient = new FileHttpClient(fileFeignClient, circuitBreakerFactory);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(circuitBreakerFactory.create("file")).thenReturn(circuitBreaker);
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
    @DisplayName("file-service 호출이 성공하면 그대로 끝난다")
    void deleteProjectFiles_success() {
        fileHttpClient.deleteProjectFiles(1L);

        verify(fileFeignClient).deleteByOwner("PROJECT", 1L);
    }

    @Test
    @DisplayName("file-service 호출이 실패해도 예외를 던지지 않는다 (best-effort, 프로젝트 삭제 자체는 막지 않음)")
    void deleteProjectFiles_failure_swallowsSilently() {
        doThrow(new RuntimeException("connection refused")).when(fileFeignClient).deleteByOwner("PROJECT", 1L);

        fileHttpClient.deleteProjectFiles(1L);
    }
}
