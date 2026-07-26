package com.growmighty.lectures.firstday.board.comment.presentation;

import com.growmighty.lectures.firstday.board.comment.application.CommentService;
import com.growmighty.lectures.firstday.board.comment.application.dto.DeleteCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterReplyCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.UpdateCommentCommand;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import com.growmighty.lectures.firstday.board.comment.presentation.dto.CommentRequest;
import com.growmighty.lectures.firstday.board.comment.presentation.dto.CommentResponse;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사용자 의견·문의 API — project 본문/ProjectNotice/Review 세 대상에 공통으로 달린다 */
@RestController
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @PostMapping("/projects/{projectId}/comments")
    public CommentResponse register(@PathVariable Long projectId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                     @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(commentService.register(
            new RegisterCommentCommand(CommentTargetType.PROJECT, projectId, authorId, request.content())));
    }

    @GetMapping("/projects/{projectId}/comments")
    public List<CommentResponse> getByProject(@PathVariable Long projectId) {
        return CommentResponse.from(commentService.getByTarget(CommentTargetType.PROJECT, projectId));
    }

    @PostMapping("/projects/{projectId}/notices/{noticeId}/comments")
    public CommentResponse registerOnNotice(@PathVariable Long noticeId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                             @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(commentService.register(
            new RegisterCommentCommand(CommentTargetType.PROJECT_NOTICE, noticeId, authorId, request.content())));
    }

    @GetMapping("/projects/{projectId}/notices/{noticeId}/comments")
    public List<CommentResponse> getByNotice(@PathVariable Long noticeId) {
        return CommentResponse.from(commentService.getByTarget(CommentTargetType.PROJECT_NOTICE, noticeId));
    }

    @PostMapping("/projects/{projectId}/reviews/{reviewId}/comments")
    public CommentResponse registerOnReview(@PathVariable Long reviewId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                             @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(commentService.register(
            new RegisterCommentCommand(CommentTargetType.REVIEW, reviewId, authorId, request.content())));
    }

    @GetMapping("/projects/{projectId}/reviews/{reviewId}/comments")
    public List<CommentResponse> getByReview(@PathVariable Long reviewId) {
        return CommentResponse.from(commentService.getByTarget(CommentTargetType.REVIEW, reviewId));
    }

    /** 답글 등록 (창작자 응대 포함) — 부모 댓글 기준으로 대상을 물려받는다 */
    @PostMapping("/comments/{commentId}/replies")
    public CommentResponse registerReply(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                          @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(
            commentService.registerReply(new RegisterReplyCommand(commentId, authorId, request.content())));
    }

    @PatchMapping("/comments/{commentId}")
    public CommentResponse update(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                   @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(
            commentService.update(new UpdateCommentCommand(commentId, requesterId, request.content())));
    }

    @DeleteMapping("/comments/{commentId}")
    public Void delete(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        commentService.delete(new DeleteCommentCommand(commentId, requesterId, requesterRole));
        return null;
    }
}