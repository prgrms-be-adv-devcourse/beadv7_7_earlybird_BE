package com.growmighty.lectures.firstday.user.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.application.TokenProvider;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.UpdateProfileCommand;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.user.presentation.dto.UpdateProfileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("PATCH /api/v1/users/me 는 X-User-Id 헤더의 사용자 정보를 요청 본문으로 수정한다")
    void updateMe_success() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("김하나둘", "010-1111-2222");
        when(userService.updateProfile(new UpdateProfileCommand(1L, "김하나둘", "010-1111-2222")))
                .thenReturn(new UserInfo(1L, "hanahan@example.com", "김하나둘", "010-1111-2222", UserRole.BACKER));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("김하나둘"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1111-2222"));
    }

    @Test
    @DisplayName("존재하지 않는 유저의 정보 수정은 404와 ENTITY_NOT_FOUND 코드를 반환한다")
    void updateMe_notFound_404() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("김하나둘", "010-1111-2222");
        when(userService.updateProfile(new UpdateProfileCommand(999L, "김하나둘", "010-1111-2222")))
                .thenThrow(new EntityNotFoundException("존재하지 않는 유저입니다. userId=999"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C003"));
    }
}
