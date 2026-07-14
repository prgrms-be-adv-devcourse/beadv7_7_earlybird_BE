package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.presentation.dto.LoginRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.RegisterUserRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    @PostMapping
    public ApiResponse<UserResponse> register(@RequestBody RegisterUserRequest request) {
        return ApiResponse.ok(UserResponse.from(userService.register(request.toCommand())));
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@RequestBody LoginRequest request) {
        return ApiResponse.ok(UserResponse.from(userService.authenticate(request.toCommand())));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUser(@PathVariable Long userId) {
        return ApiResponse.ok(UserResponse.from(userService.getUser(userId)));
    }
}
