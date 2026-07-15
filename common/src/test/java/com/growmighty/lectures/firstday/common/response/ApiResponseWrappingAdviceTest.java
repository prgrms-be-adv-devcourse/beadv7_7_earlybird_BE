package com.growmighty.lectures.firstday.common.response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiResponseWrappingAdviceTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ApiResponseWrappingAdvice(), new TestExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("컨트롤러가 반환한 DTO를 success/data/error 봉투로 감싼다")
    void wrapsControllerReturnValueInEnvelope() throws Exception {
        mockMvc.perform(get("/test/greeting/hana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("hello, hana"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("반환값이 없으면 data는 null이 된다")
    void wrapsNullBodyAsNullData() throws Exception {
        mockMvc.perform(get("/test/nothing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("이미 ApiResponse를 반환하면 이중으로 감싸지 않는다")
    void doesNotDoubleWrapExistingApiResponse() throws Exception {
        mockMvc.perform(get("/test/already-wrapped"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("raw"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("직접 반환한 ProblemDetail은 data로 감싸지 않는다")
    void doesNotWrapDirectlyReturnedProblemDetail() throws Exception {
        mockMvc.perform(get("/test/boom-direct"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("직접 반환"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("ResponseEntity로 감싸 반환한 ProblemDetail도 data로 감싸지 않는다")
    void doesNotWrapProblemDetailInsideResponseEntity() throws Exception {
        mockMvc.perform(get("/test/boom-entity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("엔티티로 감쌈"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/greeting/{name}")
        Greeting greeting(@PathVariable("name") String name) {
            return new Greeting("hello, " + name);
        }

        @GetMapping("/test/nothing")
        Void nothing() {
            return null;
        }

        @GetMapping("/test/already-wrapped")
        ApiResponse<String> alreadyWrapped() {
            return ApiResponse.ok("raw");
        }

        @GetMapping("/test/boom-direct")
        Void boomDirect() {
            throw new DirectProblemException();
        }

        @GetMapping("/test/boom-entity")
        Void boomEntity() {
            throw new EntityWrappedProblemException();
        }
    }

    @RestControllerAdvice
    static class TestExceptionAdvice {

        // GlobalExceptionHandler의 커스텀 @ExceptionHandler들처럼 ProblemDetail을 직접 반환하는 경우
        @ExceptionHandler(DirectProblemException.class)
        ProblemDetail handleDirect() {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "직접 반환");
        }

        // ResponseEntityExceptionHandler의 프레임워크 기본 처리(handleExceptionInternal 등)처럼
        // ProblemDetail이 ResponseEntity<Object>에 담겨 반환되는 경우
        @ExceptionHandler(EntityWrappedProblemException.class)
        ResponseEntity<Object> handleEntityWrapped() {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "엔티티로 감쌈");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }
    }

    static class DirectProblemException extends RuntimeException {
    }

    static class EntityWrappedProblemException extends RuntimeException {
    }

    record Greeting(String message) {
    }
}
