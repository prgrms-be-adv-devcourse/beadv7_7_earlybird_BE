package com.growmighty.lectures.firstday.project.project.presentation;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * findAll의 keyword 파라미터에 길이 상한(@Size(max=100))이 실제로 걸리는지 검증한다(리뷰 반영 —
 * keyword 하나마다 OpenAI 임베딩 호출이 붙는 공개/비인증 엔드포인트라 무제한 길이는 비용 남용 표면).
 * @Validated(클래스 레벨) + @Size(파라미터 레벨) 조합이 실패하면 Spring MVC가
 * HandlerMethodValidationException을 던지는데, 이건 GlobalExceptionHandler가 직접 잡지 않고
 * ResponseEntityExceptionHandler가 상속으로 제공하는 처리 경로(ProblemDetail 생성 →
 * handleExceptionInternal)를 그대로 타면서 GlobalExceptionHandler가 오버라이드한
 * handleExceptionInternal에서 표준 ApiResponse 봉투로 변환된다 — 이 흐름 전체가 실제로 동작하는지
 * 이 테스트로 확인한다(추측이 아니라 실행 결과로 검증).
 */
@WebMvcTest(ProjectController.class)
class ProjectControllerKeywordValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @Test
    @DisplayName("keyword가 100자를 넘으면 400과 표준 에러 봉투를 반환한다")
    void findAll_keywordTooLong_returns400() throws Exception {
        String tooLong = "가".repeat(101);

        mockMvc.perform(get("/api/v1/projects").param("keyword", tooLong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("keyword가 100자 이하이면 정상 통과한다")
    void findAll_keywordAtLimit_passesValidation() throws Exception {
        String atLimit = "가".repeat(100);

        mockMvc.perform(get("/api/v1/projects").param("keyword", atLimit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
