package com.growmighty.lectures.firstday.board.infrastructure.client.user;

import com.growmighty.lectures.firstday.board.application.port.UserPort;
import com.growmighty.lectures.firstday.board.application.port.dto.UserSnapshot;
import com.growmighty.lectures.firstday.board.infrastructure.client.user.dto.UserApiData;
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserHttpClient implements UserPort {

    private final UserFeignClient userFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public UserSnapshot getUser(Long userId) {
        return circuitBreakerFactory.create("user").run(
            () -> fetch(userId),
            cause -> failHard(userId, cause));
    }

    private UserSnapshot fetch(Long userId) {
        UserApiData data = userFeignClient.fetchUser(userId).data();
        return new UserSnapshot(data.id(), data.name());
    }

    // 작성자 이름은 등록 요청의 필수 값이다 — 낙관적으로 넘어가면 잘못된(빈) 이름이 그대로 저장되므로,
    // 리워드 담기와 달리 여기서는 실패를 그대로 드러낸다.
    private UserSnapshot failHard(Long userId, Throwable cause) {
        log.warn("사용자 정보 조회 실패. userId={}, 원인={}", userId, cause.toString());
        throw new ServiceUnavailableException("사용자 정보를 확인할 수 없어 요청을 처리할 수 없습니다. userId=" + userId);
    }
}