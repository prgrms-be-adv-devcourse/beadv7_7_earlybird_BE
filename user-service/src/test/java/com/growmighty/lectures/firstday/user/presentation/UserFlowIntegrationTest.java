package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.domain.User;
import com.growmighty.lectures.firstday.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * signup ~ 창작자 등록까지 실제 MySQL(Testcontainers) + 실제 JWT 발급/검증으로 확인하는
 * 시나리오 단위 통합 테스트. 개별 계층은 @WebMvcTest(UserControllerTest)로 이미 목 기반 검증이
 * 있으므로, 여기서는 계층을 관통하는 happy path 와 그 경로에서만 드러나는 부작용
 * (비밀번호 실제 해시 저장, 발급된 토큰의 실제 디코딩, DB 유니크 제약 등)에 집중한다.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class UserFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("회원가입 후 로그인하면 토큰을 발급받고, 비밀번호는 평문이 아닌 해시로 저장된다")
    void signupThenLogin_issuesTokensAndPersistsHashedPassword() throws Exception {
        signup("hana@example.com", "rawPassword1!", "김하나한", "010-1111-1111");

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hana@example.com","password":"rawPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("hana@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("BACKER"));

        User saved = userRepository.findByEmail("hana@example.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("rawPassword1!");
        assertThat(passwordEncoder.matches("rawPassword1!", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 이메일로 회원가입하면 409 를 반환한다")
    void signup_withDuplicateEmail_returns409() throws Exception {
        signup("dup@example.com", "rawPassword1!", "중복회원", "010-2222-2222");

        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dup@example.com","password":"rawPassword1!","name":"중복회원","phoneNumber":"010-2222-2222"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("틀린 비밀번호로 로그인하면 400 을 반환한다")
    void login_withWrongPassword_returns400() throws Exception {
        signup("wrongpw@example.com", "rawPassword1!", "회원", "010-3333-3333");

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"wrongpw@example.com","password":"wrongPassword!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("리프레시 토큰으로 새 access token 을 발급받으면 같은 사용자를 가리킨다")
    void refresh_afterLogin_issuesAccessTokenForSameUser() throws Exception {
        signup("refresh@example.com", "rawPassword1!", "회원", "010-4444-4444");
        String refreshToken = login("refresh@example.com", "rawPassword1!").refreshToken();
        Long userId = userRepository.findByEmail("refresh@example.com").orElseThrow().getId();

        String body = mockMvc.perform(post("/api/v1/users/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String newAccessToken = objectMapper.readTree(body).at("/data/accessToken").asText();
        Jwt jwt = jwtDecoder.decode(newAccessToken);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
    }

    @Test
    @DisplayName("내 정보만 수정하면 비밀번호는 그대로 유지된다")
    void updateProfile_withoutPasswordFields_keepsPasswordUnchanged() throws Exception {
        signup("profile@example.com", "rawPassword1!", "옛이름", "010-5555-5555");
        Long userId = userRepository.findByEmail("profile@example.com").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"새이름","phoneNumber":"010-9999-9999"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-9999-9999"));

        mockMvc.perform(get("/api/v1/users/me").header(JwtHeaders.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"));

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"profile@example.com","password":"rawPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("내 정보를 수정하면서 비밀번호를 함께 변경하면, 이후 로그인은 새 비밀번호로만 성공한다")
    void updateProfileWithPassword_reflectsInSubsequentLogin() throws Exception {
        signup("profile2@example.com", "rawPassword1!", "옛이름", "010-5555-5556");
        Long userId = userRepository.findByEmail("profile2@example.com").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(JwtHeaders.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"새이름","phoneNumber":"010-9999-9998","currentPassword":"rawPassword1!","newPassword":"newPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.phoneNumber").value("010-9999-9998"));

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"profile2@example.com","password":"rawPassword1!"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"profile2@example.com","password":"newPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("판매자로 등록하면 role 이 CREATOR 로 전환되고, 다시 등록하면 409 를 반환한다")
    void registerAsCreator_thenDuplicateRegistration_returns409() throws Exception {
        signup("creator@example.com", "rawPassword1!", "창작자", "010-6666-6666");
        Long userId = userRepository.findByEmail("creator@example.com").orElseThrow().getId();

        mockMvc.perform(post("/api/v1/users/me/creator")
                        .header(JwtHeaders.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankName":"신한은행","accountNumber":"110-123-456789","accountHolder":"창작자"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CREATOR"));

        User creator = userRepository.findById(userId).orElseThrow();
        assertThat(creator.getRole()).isEqualTo(UserRole.CREATOR);

        mockMvc.perform(post("/api/v1/users/me/creator")
                        .header(JwtHeaders.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankName":"신한은행","accountNumber":"110-123-456789","accountHolder":"창작자"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("internal API 는 Eureka 호출을 흉내낸 경로 변수만으로 사용자 정보를 반환한다")
    void internalGetUser_returnsUserById() throws Exception {
        signup("internal@example.com", "rawPassword1!", "내부호출대상", "010-7777-7777");
        Long userId = userRepository.findByEmail("internal@example.com").orElseThrow().getId();

        mockMvc.perform(get("/internal/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("internal@example.com"));
    }

    private void signup(String email, String password, String name, String phoneNumber) throws Exception {
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","name":"%s","phoneNumber":"%s"}
                                """.formatted(email, password, name, phoneNumber)))
                .andExpect(status().isOk());
    }

    private LoginTokens login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(body).get("data");
        return new LoginTokens(data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    private record LoginTokens(String accessToken, String refreshToken) {
    }
}
