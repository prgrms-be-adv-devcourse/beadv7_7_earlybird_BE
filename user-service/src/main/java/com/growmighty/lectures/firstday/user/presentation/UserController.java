package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.application.TokenProvider;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.user.presentation.dto.ChangePasswordRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.LoginRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.LoginResponse;
import com.growmighty.lectures.firstday.user.presentation.dto.RefreshRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.RefreshResponse;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterCreatorRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterUserRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final TokenProvider tokenProvider;

    @PostMapping("/signup")
    public UserResponse signup(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.from(userService.register(request.toCommand()));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        UserInfo info = userService.authenticate(request.toCommand());
        String accessToken = tokenProvider.issueAccessToken(info.id(), info.role());
        String refreshToken = tokenProvider.issueRefreshToken(info.id());
        return LoginResponse.of(accessToken, refreshToken, info);
    }

    /** 리프레시 토큰으로 새 access token 을 발급한다. 리프레시 토큰 자체는 갱신하지 않는다(로테이션은 범위 밖). */
    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        Long userId = tokenProvider.parseRefreshToken(request.refreshToken());
        UserInfo info = userService.getUser(userId);
        String accessToken = tokenProvider.issueAccessToken(info.id(), info.role());
        return new RefreshResponse(accessToken);
    }

    /**
     * 로그아웃. refresh token 은 서버에 저장되지 않는 stateless 방식이라(docs/3_JWT_AUTH.md §범위 밖)
     * 실제로 폐기하지는 않는다 — 주어진 토큰이 유효한지만 검증하고, 유효하면 204 를 반환한다.
     * 클라이언트가 보관 중인 access/refresh token 을 직접 삭제해야 로그아웃이 완료된다.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        tokenProvider.parseRefreshToken(request.refreshToken());
    }

    /** 내 정보 조회. userId 는 gateway 가 검증한 JWT 에서 추출해 X-User-Id 헤더로 전달한다. */
    @GetMapping("/me")
    public UserResponse getMe(@RequestHeader(JwtHeaders.USER_ID) Long userId) {
        return UserResponse.from(userService.getUser(userId));
    }

    /** 판매자(창작자) 등록. userId 는 gateway 가 검증한 JWT 에서 추출해 X-User-Id 헤더로 전달한다. */
    @PostMapping("/me/creator")
    public UserResponse registerAsCreator(@RequestHeader(JwtHeaders.USER_ID) Long userId,
                                           @Valid @RequestBody RegisterCreatorRequest request) {
        return UserResponse.from(userService.registerAsCreator(request.toCommand(userId)));
    }

    /** 비밀번호 변경. userId 는 gateway 가 검증한 JWT 에서 추출해 X-User-Id 헤더로 전달한다. */
    @PatchMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader(JwtHeaders.USER_ID) Long userId,
                                @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request.toCommand(userId));
    }
}
