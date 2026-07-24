package com.growmighty.lectures.firstday.board.comment.presentation;

import com.growmighty.lectures.firstday.board.comment.application.CommentService;
import com.growmighty.lectures.firstday.board.comment.presentation.dto.CommentRequest;
import com.growmighty.lectures.firstday.board.comment.presentation.dto.CommentResponse;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 사용자 의견·문의 API */
@RestController
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @PostMapping("/projects/{projectId}/comments")
    public CommentResponse register(@PathVariable Long projectId, @RequestBody CommentRequest request) {
        return CommentResponse.from(
            commentService.register(projectId, request.userId(), request.content()));
    }

    @GetMapping("/projects/{projectId}/comments")
    public List<CommentResponse> getByProject(@PathVariable Long projectId) {
        return commentService.getByProject(projectId).stream().map(CommentResponse::from).toList();
    }

    /** 답글 등록 (창작자 응대 포함) — 부모 댓글 기준으로 프로젝트를 물려받는다 */
    @PostMapping("/comments/{commentId}/replies")
    public CommentResponse registerReply(@PathVariable Long commentId, @RequestBody CommentRequest request) {
        return CommentResponse.from(
            commentService.registerReply(commentId, request.userId(), request.content()));
    }

    @DeleteMapping("/comments/{commentId}")
    public Void delete(@PathVariable Long commentId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        commentService.delete(commentId, requesterId, requesterRole);
        return null;
    }
}
