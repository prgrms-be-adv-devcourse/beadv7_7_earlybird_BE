package com.growmighty.lectures.firstday.board.review.presentation;

import com.growmighty.lectures.firstday.board.review.application.ReviewService;
import com.growmighty.lectures.firstday.board.review.application.dto.DeleteReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.dto.RegisterReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.dto.UpdateReviewCommand;
import com.growmighty.lectures.firstday.board.review.presentation.dto.ReviewRequest;
import com.growmighty.lectures.firstday.board.review.presentation.dto.ReviewResponse;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 리뷰 API */
@RestController
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("/projects/{projectId}/reviews")
    public ReviewResponse register(@PathVariable Long projectId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                    @Valid @RequestBody ReviewRequest request) {
        return ReviewResponse.from(reviewService.register(
            new RegisterReviewCommand(projectId, request.rewardId(), authorId, request.rating(), request.content())));
    }

    @GetMapping("/projects/{projectId}/reviews")
    public List<ReviewResponse> getByProject(@PathVariable Long projectId) {
        return reviewService.getByProject(projectId).stream().map(ReviewResponse::from).toList();
    }

    @PatchMapping("/projects/{projectId}/reviews/{reviewId}")
    public ReviewResponse update(@PathVariable Long reviewId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                  @Valid @RequestBody ReviewRequest request) {
        return ReviewResponse.from(reviewService.update(
            new UpdateReviewCommand(reviewId, requesterId, request.rating(), request.content())));
    }

    @DeleteMapping("/projects/{projectId}/reviews/{reviewId}")
    public Void delete(@PathVariable Long reviewId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        reviewService.delete(new DeleteReviewCommand(reviewId, requesterId, requesterRole));
        return null;
    }
}