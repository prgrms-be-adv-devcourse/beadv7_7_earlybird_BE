package com.growmighty.lectures.firstday.board.notice.presentation;

import com.growmighty.lectures.firstday.board.notice.application.ProjectNoticeService;
import com.growmighty.lectures.firstday.board.notice.application.dto.DeleteProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.ProjectNoticeResult;
import com.growmighty.lectures.firstday.board.notice.domain.ProjectNoticeStatus;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(ProjectNoticeController.class)
class ProjectNoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectNoticeService noticeService;

    private static final ProjectNoticeResult NOTICE = new ProjectNoticeResult(
            1L, 10L, 100L, "creator", "제목", "내용", 0L, ProjectNoticeStatus.ACTIVE,
            LocalDateTime.now(), LocalDateTime.now());

    @Test
    @DisplayName("POST /api/v1/notices?projectId= 는 projectId 쿼리 파라미터로 공지를 등록한다")
    void register_withProjectIdQueryParam_success() throws Exception {
        when(noticeService.register(any())).thenReturn(NOTICE);

        mockMvc.perform(post("/api/v1/notices")
                        .param("projectId", "10")
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(10))
                .andExpect(jsonPath("$.data.title").value("제목"));
    }

    @Test
    @DisplayName("POST /api/v1/notices 는 projectId 쿼리 파라미터가 없으면 400 을 반환한다")
    void register_withoutProjectIdQueryParam_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/notices")
                        .header(JwtHeaders.USER_ID, "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/notices?projectId= 는 해당 프로젝트의 공지 목록을 반환한다")
    void getByProject_withProjectIdQueryParam_success() throws Exception {
        when(noticeService.getByProject(10L)).thenReturn(List.of(NOTICE));

        mockMvc.perform(get("/api/v1/notices").param("projectId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));

        verify(noticeService).getByProject(10L);
    }

    @Test
    @DisplayName("GET /api/v1/notices/{noticeId} 는 단건 공지를 반환한다")
    void getNotice_byPathVariable_success() throws Exception {
        when(noticeService.getNotice(1L)).thenReturn(NOTICE);

        mockMvc.perform(get("/api/v1/notices/{noticeId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PATCH /api/v1/notices/{noticeId} 는 요청자 정보를 헤더로 받아 공지를 수정한다")
    void update_byPathVariable_success() throws Exception {
        when(noticeService.update(any())).thenReturn(NOTICE);

        mockMvc.perform(patch("/api/v1/notices/{noticeId}", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .header(JwtHeaders.USER_ROLE, UserRole.CREATOR.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정된 제목\",\"content\":\"수정된 내용\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/notices/{noticeId} 는 요청자 정보를 헤더로 받아 공지를 삭제한다")
    void delete_byPathVariable_success() throws Exception {
        mockMvc.perform(delete("/api/v1/notices/{noticeId}", 1L)
                        .header(JwtHeaders.USER_ID, "100")
                        .header(JwtHeaders.USER_ROLE, UserRole.CREATOR.name()))
                .andExpect(status().isOk());

        verify(noticeService).delete(new DeleteProjectNoticeCommand(1L, 100L, UserRole.CREATOR));
    }
}