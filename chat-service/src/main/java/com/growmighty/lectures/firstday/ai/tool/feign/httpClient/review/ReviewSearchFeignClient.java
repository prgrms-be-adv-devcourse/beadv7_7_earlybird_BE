package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.review;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.review.dto.ReviewSearchApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "board-service")
public interface ReviewSearchFeignClient {

    @GetMapping("/api/v1/reviews")
    ApiResponse<List<ReviewSearchApiData>> search(@RequestParam Long projectId);
}
