package com.growmighty.lectures.firstday.board.review.presentation;

import com.growmighty.lectures.firstday.board.review.application.ReviewService;
import com.growmighty.lectures.firstday.board.review.application.dto.DeleteReviewCommand;
import com.growmighty.lectures.firstday.board.review.application.dto.ReviewResult;
import com.growmighty.lectures.firstday.board.review.domain.ReviewStatus;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    private static final ReviewResult REVIEW = new ReviewResult(
            1L, 10L, 200L, "리워드A", 100L, "backer", BigDecimal.valueOf(5), "좋아요",
            ReviewStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

    @Test
    @DisplayName("POST /api/v1/reviews?projectId= 는 projectId 쿼리 파라미터로 리뷰를 등록한다")
    void register_withProjectIdQueryParam_success() throws Exception {
        when(reviewService.register(any())).thenReturn(REVIEW);

        mockMvc.perform(post("/api/v1/reviews")
                        .param("projectId", "10")
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":200,\"rating\":5,\"content\":\"좋아요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/reviews 는 projectId 쿼리 파라미터가 없으면 400 을 반환한다")
    void register_withoutProjectIdQueryParam_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rewardId\":200,\"rating\":5,\"content\":\"좋아요\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/reviews?projectId= 는 해당 프로젝트의 리뷰 목록을 반환한다")
    void getByProject_withProjectIdQueryParam_success() throws Exception {
        when(reviewService.getByProject(10L)).thenReturn(List.of(REVIEW));

        mockMvc.perform(get("/api/v1/reviews").param("projectId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));

        verify(reviewService).getByProject(10L);
    }

    @Test
    @DisplayName("PATCH /api/v1/reviews/{reviewId} 는 요청자 정보를 헤더로 받아 리뷰를 수정한다")
    void update_byPathVariable_success() throws Exception {
        when(reviewService.update(any())).thenReturn(REVIEW);

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"수정된 리뷰\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/reviews/{reviewId} 는 요청자 정보를 헤더로 받아 리뷰를 삭제한다")
    void delete_byPathVariable_success() throws Exception {
        mockMvc.perform(delete("/api/v1/reviews/{reviewId}", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isOk());

        verify(reviewService).delete(new DeleteReviewCommand(1L, 100L, UserRole.BACKER));
    }
}