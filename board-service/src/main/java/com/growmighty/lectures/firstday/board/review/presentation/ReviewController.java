package com.growmighty.lectures.firstday.board.review.presentation;

import com.growmighty.lectures.firstday.board.review.application.ReviewService;
import com.growmighty.lectures.firstday.board.review.presentation.dto.ReviewRequest;
import com.growmighty.lectures.firstday.board.review.presentation.dto.ReviewResponse;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 리뷰 API */
@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("/projects/{projectId}/reviews")
    public ApiResponse<ReviewResponse> register(@PathVariable Long projectId, @RequestBody ReviewRequest request) {
        return ApiResponse.ok(ReviewResponse.from(
            reviewService.register(projectId, request.userId(), request.rating(), request.content())));
    }

    @GetMapping("/projects/{projectId}/reviews")
    public ApiResponse<List<ReviewResponse>> getByProject(@PathVariable Long projectId) {
        return ApiResponse.ok(reviewService.getByProject(projectId).stream().map(ReviewResponse::from).toList());
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ApiResponse<Void> delete(@PathVariable Long reviewId) {
        reviewService.delete(reviewId);
        return ApiResponse.ok();
    }
}
