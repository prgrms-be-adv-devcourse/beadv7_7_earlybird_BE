package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service")
public interface OrderFeignClient {

    @GetMapping("/internal/v1/orders/{projectId}/ordered-existence")
    ApiResponse<Boolean> hasOrderedReward(@PathVariable("projectId") Long projectId);
}
