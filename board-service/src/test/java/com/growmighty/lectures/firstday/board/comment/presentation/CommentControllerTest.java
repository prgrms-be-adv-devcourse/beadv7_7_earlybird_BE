package com.growmighty.lectures.firstday.board.comment.presentation;

import com.growmighty.lectures.firstday.board.comment.application.CommentService;
import com.growmighty.lectures.firstday.board.comment.application.dto.CommentResult;
import com.growmighty.lectures.firstday.board.comment.application.dto.DeleteCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterCommentCommand;
import com.growmighty.lectures.firstday.board.comment.application.dto.RegisterReplyCommand;
import com.growmighty.lectures.firstday.board.comment.domain.CommentStatus;
import com.growmighty.lectures.firstday.board.comment.domain.CommentTargetType;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    private static CommentResult comment(CommentTargetType targetType, Long targetId) {
        return new CommentResult(1L, targetType, targetId, 100L, "user", null, "내용",
                CommentStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now(), List.of());
    }

    @ParameterizedTest
    @EnumSource(CommentTargetType.class)
    @DisplayName("POST /api/v1/comments?targetType=&targetId= 는 대상 종류에 상관없이 댓글을 등록한다")
    void register_withTargetTypeAndTargetId_success(CommentTargetType targetType) throws Exception {
        when(commentService.register(any())).thenReturn(comment(targetType, 10L));

        mockMvc.perform(post("/api/v1/comments")
                        .param("targetType", targetType.name())
                        .param("targetId", "10")
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value(targetType.name()))
                .andExpect(jsonPath("$.data.targetId").value(10));

        verify(commentService).register(new RegisterCommentCommand(targetType, 10L, 100L, "내용"));
    }

    @ParameterizedTest
    @EnumSource(CommentTargetType.class)
    @DisplayName("GET /api/v1/comments?targetType=&targetId= 는 대상 종류별로 댓글 목록을 조회한다")
    void getByTarget_withTargetTypeAndTargetId_success(CommentTargetType targetType) throws Exception {
        when(commentService.getByTarget(targetType, 10L)).thenReturn(List.of(comment(targetType, 10L)));

        mockMvc.perform(get("/api/v1/comments")
                        .param("targetType", targetType.name())
                        .param("targetId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetType").value(targetType.name()));

        verify(commentService).getByTarget(targetType, 10L);
    }

    @Test
    @DisplayName("GET /api/v1/comments 는 targetType 값이 유효하지 않으면 400 을 반환한다")
    void getByTarget_withInvalidTargetType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/comments")
                        .param("targetType", "INVALID")
                        .param("targetId", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/comments/{commentId}/replies 는 부모 댓글 기준으로 답글을 등록한다")
    void registerReply_byPathVariable_success() throws Exception {
        when(commentService.registerReply(any())).thenReturn(comment(CommentTargetType.PROJECT_NOTICE, 10L));

        mockMvc.perform(post("/api/v1/comments/{commentId}/replies", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답글\"}"))
                .andExpect(status().isOk());

        verify(commentService).registerReply(new RegisterReplyCommand(1L, 100L, "답글"));
    }

    @Test
    @DisplayName("PATCH /api/v1/comments/{commentId} 는 요청자 정보를 헤더로 받아 댓글을 수정한다")
    void update_byPathVariable_success() throws Exception {
        when(commentService.update(any())).thenReturn(comment(CommentTargetType.PROJECT, 10L));

        mockMvc.perform(patch("/api/v1/comments/{commentId}", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정된 댓글\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{commentId} 는 요청자 정보를 헤더로 받아 댓글을 삭제한다")
    void delete_byPathVariable_success() throws Exception {
        mockMvc.perform(delete("/api/v1/comments/{commentId}", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isOk());

        verify(commentService).delete(new DeleteCommentCommand(1L, 100L, UserRole.BACKER));
    }
}