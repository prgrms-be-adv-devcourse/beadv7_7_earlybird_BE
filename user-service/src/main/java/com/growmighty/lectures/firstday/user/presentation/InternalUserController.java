package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/users")
public class InternalUserController {
	private final UserService userService;

	/** 다른 서비스가 Eureka 를 통해 직접 호출하는 내부 API. userId 를 경로로 직접 전달받는다. */
	@GetMapping("/{userId}")
	public UserResponse getUser(@PathVariable Long userId) {
		return UserResponse.from(userService.getUser(userId));
	}

}
