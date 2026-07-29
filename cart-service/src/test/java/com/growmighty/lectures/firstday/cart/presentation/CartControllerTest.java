package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.GlobalExceptionHandler;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerTest {

    @Disabled
    @Test
    @DisplayName("DELETE one cart item requires no request body")
    void removeItem_requiresNoRequestBody() throws Exception {
        CartService cartService = mock(CartService.class);
        when(cartService.removeItem(1L, 101L)).thenReturn(emptyCartView());
        MockMvc mockMvc = mockMvc(cartService);

        mockMvc.perform(delete("/users/1/cart/items/101")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isOk());
    }

    @Disabled
    @Test
    @DisplayName("BACKER can access own cart")
    void getCart_backerOwnCart_success() throws Exception {
        CartService cartService = mock(CartService.class);
        when(cartService.getCart(1L)).thenReturn(emptyCartView());
        MockMvc mockMvc = mockMvc(cartService);

        mockMvc.perform(get("/users/1/cart")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isOk());

        verify(cartService).getCart(1L);
    }

    @Disabled
    @Test
    @DisplayName("non-BACKER cannot access cart")
    void getCart_nonBacker_forbidden() throws Exception {
        CartService cartService = mock(CartService.class);
        MockMvc mockMvc = mockMvc(cartService);

        mockMvc.perform(get("/users/1/cart")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.CREATOR.name()))
                .andExpect(status().isForbidden());

        verify(cartService, never()).getCart(1L);
    }

    @Disabled
    @Test
    @DisplayName("BACKER cannot access another user's cart")
    void getCart_otherUser_forbidden() throws Exception {
        CartService cartService = mock(CartService.class);
        MockMvc mockMvc = mockMvc(cartService);

        mockMvc.perform(get("/users/2/cart")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isForbidden());

        verify(cartService, never()).getCart(1L);
    }

    @Disabled
    @Test
    @DisplayName("old operation-based project PATCH endpoint is removed")
    void oldProjectPatchEndpoint_isRemoved() throws Exception {
        CartService cartService = mock(CartService.class);
        MockMvc mockMvc = mockMvc(cartService);

        mockMvc.perform(patch("/users/1/cart/projects/10/items")
                        .contentType("application/json")
                        .content("""
                                {"items":[{"rewardId":101,"operation":"INCREMENT","quantity":1}]}
                                """))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(CartService cartService) {
        return MockMvcBuilders.standaloneSetup(new CartController(cartService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static CartView emptyCartView() {
        return new CartView(
                1L,
                1L,
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
