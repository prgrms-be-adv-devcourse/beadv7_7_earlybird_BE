package com.growmighty.lectures.firstday.settlement.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(JpaAuditingWebMvcSliceTest.ProbeController.class)
@Import(JpaAuditingWebMvcSliceTest.ProbeController.class)
class JpaAuditingWebMvcSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("JPA가 없는 Web MVC 슬라이스는 Settlement JPA Auditing 설정 때문에 실패하지 않는다")
    void startsWithoutJpaInfrastructure() throws Exception {
        mockMvc.perform(get("/test/auditing-probe"))
                .andExpect(status().isOk());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/test/auditing-probe")
        ProbeResponse probe() {
            return new ProbeResponse("ok");
        }
    }

    record ProbeResponse(String status) {
    }
}
