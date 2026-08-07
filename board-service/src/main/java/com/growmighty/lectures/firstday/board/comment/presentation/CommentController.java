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
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @PostMapping
    public CommentResponse register(@RequestParam CommentTargetType targetType, @RequestParam Long targetId,
                                     @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                     @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(commentService.register(
            new RegisterCommentCommand(targetType, targetId, authorId, request.content())));
    }

    @GetMapping
    public List<CommentResponse> getByTarget(@RequestParam CommentTargetType targetType, @RequestParam Long targetId) {
        return CommentResponse.from(commentService.getByTarget(targetType, targetId));
    }

    /** 답글 등록 (창작자 응대 포함) — 부모 댓글 기준으로 대상을 물려받는다 */
    @PostMapping("/{commentId}/replies")
    public CommentResponse registerReply(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                          @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(
            commentService.registerReply(new RegisterReplyCommand(commentId, authorId, request.content())));
    }

    @PatchMapping("/{commentId}")
    public CommentResponse update(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                   @Valid @RequestBody CommentRequest request) {
        return CommentResponse.from(
            commentService.update(new UpdateCommentCommand(commentId, requesterId, request.content())));
    }

    @DeleteMapping("/{commentId}")
    public Void delete(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        commentService.delete(new DeleteCommentCommand(commentId, requesterId, requesterRole));
        return null;
    }
}