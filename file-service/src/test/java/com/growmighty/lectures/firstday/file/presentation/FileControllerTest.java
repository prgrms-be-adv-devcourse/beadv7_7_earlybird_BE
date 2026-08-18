package com.growmighty.lectures.firstday.file.presentation;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.file.application.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** register가 @Valid 없이 Lombok @NonNull만 믿던 시절엔 잘못된 요청이 400 대신 500으로 샜다 — 실제로 400인지 HTTP 계층에서 확인한다. */
@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @Test
    @DisplayName("register: storedUrl이 비어있으면 400으로 거부되고 서비스는 호출되지 않는다")
    void register_blankStoredUrl_rejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/files")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerType":"PROJECT","ownerId":1,"storedUrl":"","originalName":"a.jpg",
                                 "contentType":"image/jpeg","fileSize":100,"sortOrder":0}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("register: fileSize가 0 이하면 400으로 거부되고 서비스는 호출되지 않는다")
    void register_nonPositiveFileSize_rejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/files")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerType":"PROJECT","ownerId":1,"storedUrl":"https://cdn.example.com/files/1/a.jpg",
                                 "originalName":"a.jpg","contentType":"image/jpeg","fileSize":0,"sortOrder":0}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("register: X-User-Id 헤더가 없으면 400으로 거부되고 서비스는 호출되지 않는다")
    void register_missingUserIdHeader_rejectedWith400() throws Exception {
        mockMvc.perform(post("/api/v1/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerType":"PROJECT","ownerId":1,"storedUrl":"https://cdn.example.com/files/1/a.jpg",
                                 "originalName":"a.jpg","contentType":"image/jpeg","fileSize":100,"sortOrder":0}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileService);
    }
}
