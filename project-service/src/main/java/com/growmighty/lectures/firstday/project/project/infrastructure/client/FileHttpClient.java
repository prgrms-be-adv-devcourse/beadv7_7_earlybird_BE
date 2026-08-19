package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import com.growmighty.lectures.firstday.project.project.application.port.FilePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileHttpClient implements FilePort {

    private final FileFeignClient fileFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public void deleteProjectFiles(Long projectId) {
        circuitBreakerFactory.create("file").run(
            () -> {
                fileFeignClient.deleteByOwner("PROJECT", projectId);
                return null;
            },
            cause -> deleteProjectFilesFallback(projectId, cause));
    }

    // 프로젝트는 이미 삭제가 확정된 상태 — file-service 장애로 이 정리가 실패해도 삭제 자체를
    // 막을 이유는 없다(검색 색인 정리와 동일하게 best-effort). 고아로 남은 파일은 나중에 별도로
    // 정리하면 된다.
    private Void deleteProjectFilesFallback(Long projectId, Throwable cause) {
        log.warn("프로젝트 파일 정리 실패 → 프로젝트 삭제는 계속 진행. projectId={}, 원인={}", projectId, cause.toString());
        return null;
    }
}
