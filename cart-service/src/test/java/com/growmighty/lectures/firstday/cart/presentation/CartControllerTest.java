package com.growmighty.lectures.firstday.cart.presentation;

import com.growmighty.lectures.firstday.cart.application.CartService;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerTest {

    @Test
    @DisplayName("DELETE one cart item requires no request body")
    void removeItem_requiresNoRequestBody() throws Exception {
        CartService cartService = mock(CartService.class);
        when(cartService.removeItem(1L, 101L)).thenReturn(emptyCartView());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService)).build();

        mockMvc.perform(delete("/users/1/cart/items/101"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("old operation-based project PATCH endpoint is removed")
    void oldProjectPatchEndpoint_isRemoved() throws Exception {
        CartService cartService = mock(CartService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CartController(cartService)).build();

        mockMvc.perform(patch("/users/1/cart/projects/10/items")
                        .contentType("application/json")
                        .content("""
                                {"items":[{"rewardId":101,"operation":"INCREMENT","quantity":1}]}
                                """))
                .andExpect(status().isNotFound());
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
