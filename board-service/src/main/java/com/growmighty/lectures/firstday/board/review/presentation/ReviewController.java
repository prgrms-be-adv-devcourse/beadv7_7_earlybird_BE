package com.growmighty.lectures.firstday.board.review.presentation;

import com.growmighty.lectures.firstday.board.review.application.ReviewService;
import com.growmighty.lectures.firstday.board.review.presentation.dto.ReviewRequest;
import com.growmighty.lectures.firstday.board.review.presentation.dto.ReviewResponse;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 리뷰 API */
@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("/projects/{projectId}/reviews")
    public ReviewResponse register(@PathVariable Long projectId, @RequestBody ReviewRequest request) {
        return ReviewResponse.from(
            reviewService.register(projectId, request.orderId(), request.authorId(), request.rating(), request.content()));
    }

    @GetMapping("/projects/{projectId}/reviews")
    public List<ReviewResponse> getByProject(@PathVariable Long projectId) {
        return reviewService.getByProject(projectId).stream().map(ReviewResponse::from).toList();
    }

    @DeleteMapping("/reviews/{reviewId}")
    public Void delete(@PathVariable Long reviewId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        reviewService.delete(reviewId, requesterId, requesterRole);
        return null;
    }
}
