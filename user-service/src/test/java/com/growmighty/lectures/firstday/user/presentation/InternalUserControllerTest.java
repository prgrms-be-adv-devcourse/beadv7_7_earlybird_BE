package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.user.application.UserService;
import com.growmighty.lectures.firstday.user.application.dto.UserInfo;
import com.growmighty.lectures.firstday.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalUserController.class)
class InternalUserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("GET /internal/v1/users/{userId} 는 경로의 userId 로 조회한 사용자 정보를 반환한다")
	void getUser_success() throws Exception {
		when(userService.getUser(1L))
				.thenReturn(new UserInfo(1L, "hanahan@example.com", "김하나한", "010-0000-0000", UserRole.USER));

        String contentAsString = mockMvc.perform(get("/internal/v1/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("hanahan@example.com"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        System.out.println(contentAsString);
    }

	@Test
	@DisplayName("존재하지 않는 userId 조회는 404와 ENTITY_NOT_FOUND 코드를 반환한다")
	void getUser_notFound_404() throws Exception {
		when(userService.getUser(999L))
				.thenThrow(new EntityNotFoundException("존재하지 않는 유저입니다. userId=999"));

		mockMvc.perform(get("/internal/v1/users/999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.error.code").value("C003"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}
}
