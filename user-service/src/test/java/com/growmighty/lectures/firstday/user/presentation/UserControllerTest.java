package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.ErrorCode;
import com.growmighty.lectures.firstday.user.application.TokenProvider;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TokenProvider tokenProvider;

    private static final UserInfo BACKER =
            new UserInfo(1L, "hanahan@example.com", "김하나한", "010-0000-0000", UserRole.BACKER);

    @Test
    @DisplayName("POST /api/v1/users/signup 은 필수 필드가 빈 값이면 400 과 C001 을 반환한다")
    void signup_withBlankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\",\"name\":\"\",\"phoneNumber\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("POST /api/v1/users/login 은 email 이 빈 값이면 400 과 C001 을 반환한다")
    void login_withBlankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"rawPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("POST /api/v1/users/login 은 email 형식이 아니면 400 과 C001 을 반환한다")
    void login_withInvalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"rawPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("POST /api/v1/users/login 은 access token 과 refresh token 을 함께 반환한다")
    void login_returnsAccessAndRefreshToken() throws Exception {
        when(userService.authenticate(any())).thenReturn(BACKER);
        when(tokenProvider.issueAccessToken(1L, UserRole.BACKER)).thenReturn("access-token");
        when(tokenProvider.issueRefreshToken(1L)).thenReturn("refresh-token");

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hanahan@example.com\",\"password\":\"rawPassword1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.id").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/users/refresh 는 refreshToken 이 빈 값이면 400 과 C001 을 반환한다")
    void refresh_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("POST /api/v1/users/refresh 는 유효한 리프레시 토큰이면 새 access token 을 반환한다")
    void refresh_withValidToken_returnsNewAccessToken() throws Exception {
        when(tokenProvider.parseRefreshToken("valid-refresh-token")).thenReturn(1L);
        when(userService.getUser(1L)).thenReturn(BACKER);
        when(tokenProvider.issueAccessToken(1L, UserRole.BACKER)).thenReturn("new-access-token");

        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("POST /api/v1/users/refresh 는 유효하지 않은 리프레시 토큰이면 401 을 반환한다")
    void refresh_withInvalidToken_returns401() throws Exception {
        when(tokenProvider.parseRefreshToken(eq("bad-token")))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 리프레시 토큰입니다."));

        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C401"));
    }

    @Test
    @DisplayName("POST /api/v1/users/logout 은 유효한 리프레시 토큰이면 204 를 반환한다")
    void logout_withValidToken_returnsNoContent() throws Exception {
        when(tokenProvider.parseRefreshToken("valid-refresh-token")).thenReturn(1L);

        mockMvc.perform(post("/api/v1/users/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/users/logout 은 유효하지 않은 리프레시 토큰이면 401 을 반환한다")
    void logout_withInvalidToken_returns401() throws Exception {
        when(tokenProvider.parseRefreshToken(eq("bad-token")))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 리프레시 토큰입니다."));

        mockMvc.perform(post("/api/v1/users/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C401"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password 는 필수 필드가 빈 값이면 400 과 C001 을 반환한다")
    void changePassword_withBlankFields_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password 는 현재 비밀번호가 틀리면 400 과 C001 을 반환한다")
    void changePassword_withWrongCurrentPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다."))
                .when(userService).changePassword(any());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrongPassword1!\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("C001"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/password 는 정상 요청이면 204 를 반환한다")
    void changePassword_withValidRequest_returnsNoContent() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"rawPassword1!\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(any());
    }
}
