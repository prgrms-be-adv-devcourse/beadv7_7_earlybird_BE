package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.presentation.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 서비스 간 내부 API (API 명세서 §9). Gateway 를 거치지 않고
 * 다른 서비스가 Eureka 로 직접 호출한다 — 후원 시 후원자 정보 확인용.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class UserInternalController {
    private final UserService userService;

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable Long userId) {
        return UserResponse.from(userService.getUser(userId));
    }
}
