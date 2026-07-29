package com.growmighty.lectures.firstday.board.feign.httpClient.project;

import com.growmighty.lectures.firstday.board.feign.port.ProjectPort;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectHttpClient implements ProjectPort {

    private final ProjectFeignClient projectFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public boolean existsProject(Long projectId) {
        return circuitBreakerFactory.create("project").run(
            () -> fetch(projectId),
            cause -> failHard(projectId, cause));
    }

    // 404(FeignException.NotFound)는 "존재하지 않음"이라는 정상적인 판정 결과다 — 서킷브레이커의 실패 집계로 넘기지 않고
    // 여기서 바로 false로 변환한다. 이 예외를 잡지 않고 흘려보내면 실제 장애(타임아웃/5xx)와 똑같이 취급되어 503으로 새어나간다.
    private boolean fetch(Long projectId) {
        try {
            projectFeignClient.getProject(projectId);
            return true;
        } catch (FeignException.NotFound e) {
            return false;
        }
    }

    private boolean failHard(Long projectId, Throwable cause) {
        log.warn("프로젝트 존재 확인 실패. projectId={}, 원인={}", projectId, cause.toString());
        throw new ServiceUnavailableException("프로젝트 정보를 확인할 수 없어 요청을 처리할 수 없습니다. projectId=" + projectId);
    }
}