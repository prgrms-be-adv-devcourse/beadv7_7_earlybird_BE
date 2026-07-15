package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.presentation.dto.LoginRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterCreatorRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterUserRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @PostMapping("/signup")
    public UserResponse signup(@RequestBody RegisterUserRequest request) {
        return UserResponse.from(userService.register(request.toCommand()));
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody LoginRequest request) {
        return UserResponse.from(userService.authenticate(request.toCommand()));
    }

    /** 내 정보 조회. TODO(팀): JWT 도입 후 userId 파라미터 대신 토큰에서 추출 */
    @GetMapping("/me")
    public UserResponse getMe(@RequestParam Long userId) {
        return UserResponse.from(userService.getUser(userId));
    }

    /** 판매자(창작자) 등록. TODO(팀): JWT 도입 후 userId 파라미터 대신 토큰에서 추출 */
    @PostMapping("/me/creator")
    public UserResponse registerAsCreator(@RequestParam Long userId, @RequestBody RegisterCreatorRequest request) {
        return UserResponse.from(userService.registerAsCreator(request.toCommand(userId)));
    }
}
