package com.growmighty.lectures.firstday.board.infrastructure.client.user;

import com.growmighty.lectures.firstday.board.infrastructure.client.user.dto.UserApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// name = Eureka 명부에 올라간 서비스 이름. 호출 시점마다 로드밸런서가 실제 주소로 치환한다.
@FeignClient(name = "user-service")
public interface UserFeignClient {

    @GetMapping("/internal/v1/users/{userId}")
    ApiResponse<UserApiData> fetchUser(@PathVariable("userId") Long userId);
}