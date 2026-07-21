package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.application.TokenProvider;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.user.presentation.dto.LoginRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.LoginResponse;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterCreatorRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterUserRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final TokenProvider tokenProvider;

    @PostMapping("/signup")
    public UserResponse signup(@RequestBody RegisterUserRequest request) {
        return UserResponse.from(userService.register(request.toCommand()));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        UserInfo info = userService.authenticate(request.toCommand());
        String accessToken = tokenProvider.issueAccessToken(info.id(), info.role());
        return LoginResponse.of(accessToken, info);
    }

    /** 내 정보 조회. userId 는 gateway 가 검증한 JWT 에서 추출해 X-User-Id 헤더로 전달한다. */
    @GetMapping("/me")
    public UserResponse getMe(@RequestHeader(JwtHeaders.USER_ID) Long userId) {
        return UserResponse.from(userService.getUser(userId));
    }

    /** 판매자(창작자) 등록. userId 는 gateway 가 검증한 JWT 에서 추출해 X-User-Id 헤더로 전달한다. */
    @PostMapping("/me/creator")
    public UserResponse registerAsCreator(@RequestHeader(JwtHeaders.USER_ID) Long userId,
                                           @RequestBody RegisterCreatorRequest request) {
        return UserResponse.from(userService.registerAsCreator(request.toCommand(userId)));
    }
}
