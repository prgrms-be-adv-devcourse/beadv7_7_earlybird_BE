package com.growmighty.lectures.firstday.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void BusinessException은_ErrorCode의_상태와_코드로_응답한다() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("C003"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.ENTITY_NOT_FOUND.getMessage()));
    }

    @Test
    void Valid_실패는_400과_필드별_오류_목록으로_응답한다() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"))
                .andExpect(jsonPath("$.error.errors[?(@.field == 'userId')]").exists())
                .andExpect(jsonPath("$.error.errors[?(@.field == 'quantity')]").exists());
    }

    @Test
    void 예상_못_한_예외는_500과_고정_메시지로_응답하고_내부_사정을_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C500"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_405로_응답한다() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void IllegalStateException은_409로_응답한다() throws Exception {
        mockMvc.perform(get("/test/state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C002"))
                .andExpect(jsonPath("$.error.message").value("이미 처리된 요청입니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        Void business() {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }

        @PostMapping("/test/valid")
        Void valid(@Valid @RequestBody SampleRequest request) {
            return null;
        }

        @GetMapping("/test/boom")
        Void boom() {
            throw new RuntimeException("secret detail");
        }

        @GetMapping("/test/state")
        Void state() {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
    }

    record SampleRequest(@NotNull Long userId, @NotNull @Positive Integer quantity) {
    }
}
