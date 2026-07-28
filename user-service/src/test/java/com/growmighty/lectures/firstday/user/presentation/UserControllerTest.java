package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.user.application.TokenProvider;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("POST /api/v1/users/signup 은 필수 필드가 빈 값이면 400 을 반환한다")
    void signup_withBlankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\",\"name\":\"\",\"phoneNumber\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/users/login 은 email 이 빈 값이면 400 을 반환한다")
    void login_withBlankEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"rawPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/users/login 은 email 형식이 아니면 400 을 반환한다")
    void login_withInvalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"rawPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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
    @DisplayName("POST /api/v1/users/refresh 는 refreshToken 이 빈 값이면 400 을 반환한다")
    void refresh_withBlankToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
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
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 리프레시 토큰입니다."));

        mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("유효하지 않거나 만료된 리프레시 토큰입니다."));
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
                .thenThrow(new BusinessException(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 리프레시 토큰입니다."));

        mockMvc.perform(post("/api/v1/users/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("유효하지 않거나 만료된 리프레시 토큰입니다."));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me 는 newPassword 가 없으면 400 을 반환한다")
    void updateMe_withoutNewPassword_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\",\"phoneNumber\":\"010-1111-2222\",\"currentPassword\":\"rawPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me 는 현재 비밀번호가 틀리면 400 을 반환한다")
    void updateMe_withWrongCurrentPassword_returns400() throws Exception {
        doThrow(new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다."))
                .when(userService).updateProfile(any());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\",\"phoneNumber\":\"010-1111-2222\",\"currentPassword\":\"wrongPassword1!\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/users/me 는 X-User-Id 헤더의 사용자 정보를 반환한다")
    void getMe_returnsCurrentUser() throws Exception {
        when(userService.getUser(1L)).thenReturn(BACKER);

        mockMvc.perform(get("/api/v1/users/me").header(JwtHeaders.USER_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("hanahan@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/creator 는 필수 필드가 빈 값이면 400 을 반환한다")
    void registerAsCreator_withBlankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/creator")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"\",\"accountNumber\":\"\",\"accountHolder\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/creator 는 정상 요청이면 CREATOR 로 전환된 사용자 정보를 반환한다")
    void registerAsCreator_withValidRequest_returnsCreatorUser() throws Exception {
        UserInfo creator = new UserInfo(1L, "hanahan@example.com", "김하나한", "010-0000-0000", UserRole.CREATOR);
        when(userService.registerAsCreator(any())).thenReturn(creator);

        mockMvc.perform(post("/api/v1/users/me/creator")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"신한은행\",\"accountNumber\":\"110-123-456789\",\"accountHolder\":\"김하나한\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CREATOR"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me 는 필수 필드가 빈 값이면 400 을 반환한다")
    void updateMe_withBlankFields_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phoneNumber\":\"\",\"currentPassword\":\"\",\"newPassword\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me 는 newPassword 가 4자 미만이면 400 을 반환한다")
    void updateMe_withShortNewPassword_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\",\"phoneNumber\":\"010-1111-2222\",\"currentPassword\":\"rawPassword1!\",\"newPassword\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me 는 이름, 전화번호, 비밀번호를 함께 수정하고 갱신된 정보를 반환한다")
    void updateMe_withValidFields_returnsUpdatedUser() throws Exception {
        UserInfo updated = new UserInfo(1L, "hanahan@example.com", "새이름", "010-1111-2222", UserRole.BACKER);
        when(userService.updateProfile(any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"새이름\",\"phoneNumber\":\"010-1111-2222\",\"currentPassword\":\"rawPassword1!\",\"newPassword\":\"newPassword1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-1111-2222"));

        verify(userService).updateProfile(any());
    }
}
