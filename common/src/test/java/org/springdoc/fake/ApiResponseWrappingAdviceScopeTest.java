package org.springdoc.fake;

import com.growmighty.lectures.firstday.common.response.ApiResponseWrappingAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * springdoc의 실제 패키지(org.springdoc.*)를 흉내낸 컨트롤러로,
 * ApiResponseWrappingAdvice의 basePackages 제한이 우리 코드 밖의 컨트롤러(예: springdoc의
 * /v3/api-docs, byte[] 반환)에는 적용되지 않는지 검증한다.
 * 이 스코프가 없으면 이미 선택된 ByteArrayHttpMessageConverter가 ApiResponse로 감싸진 값을
 * 캐스팅하다 ClassCastException을 던진다.
 */
class ApiResponseWrappingAdviceScopeTest {

    @Test
    void doesNotWrapThirdPartyControllerOutsideBasePackage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FakeApiDocsController())
                .setControllerAdvice(new ApiResponseWrappingAdvice())
                .build();

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"openapi\":\"3.0\"}"));
    }

    @RestController
    static class FakeApiDocsController {

        @GetMapping(value = "/v3/api-docs", produces = MediaType.APPLICATION_JSON_VALUE)
        byte[] apiDocs() {
            return "{\"openapi\":\"3.0\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
