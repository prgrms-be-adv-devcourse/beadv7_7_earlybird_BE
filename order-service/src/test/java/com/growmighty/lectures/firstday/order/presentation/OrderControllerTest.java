package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.order.application.OrderApiService;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Disabled;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderApiService orderApiService;

    @Disabled
    @Test
    @DisplayName("정상 응답 테스트")
    void placeOrder_success() throws Exception {
        Long orderId = 1L;
        when(orderApiService.placeOrder(any(PlaceOrderCommand.class), eq(1L))).thenReturn(result(orderId, OrderStatus.PAID));

        mockMvc.perform(post("/orders")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"projectId":10,"requests":[{"rewardId":1,"quantity":1,"expectedUnitPrice":20000}],"receiverName":"Receiver","receiverPhone":"010-0000-0000","shippingAddress":"Seoul","zipCode":"06236","expectedItemsAmount":20000,"expectedTotalAmount":23000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    @Disabled
    @Test
    @DisplayName("본인 order 내역")
    void getMyOrders_success() throws Exception {
        Long orderId = 1L;
        when(orderApiService.getOrdersByUser(1L)).thenReturn(List.of(result(orderId, OrderStatus.PAID)));

        mockMvc.perform(get("/orders/me")
                        .param("userId", "1")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(orderId));
    }

    @Disabled
    @Test
    @DisplayName("후원 상세 조회")
    void getOrder_success() throws Exception {
        Long orderId = 1L;
        when(orderApiService.getOrderInfo(orderId, 1L)).thenReturn(result(orderId, OrderStatus.PAID));

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(orderId));
    }

    @Disabled
    @Test
    @DisplayName("오더 Id 없는 것 404")
    void inspectOrder_notFound_404() throws Exception {
        Long orderId = 1L;
        when(orderApiService.getOrderInfo(orderId, 1L))
                .thenThrow(new EntityNotFoundException("Order not found. orderId=" + orderId));

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("Order not found. orderId=" + orderId));
    }

    @Disabled
    @Test
    @DisplayName("중복 취소 시 오류 리턴")
    void cancelOrder_conflict_409() throws Exception {
        Long orderId = 1L;
        when(orderApiService.cancelOrder(orderId, 1L))
                .thenThrow(new IllegalStateException("Order is already cancelled. orderId=" + orderId));

        mockMvc.perform(post("/orders/{orderId}/cancel", orderId)
                        .param("userId", "1")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("Order is already cancelled. orderId=" + orderId));
    }

    @Disabled
    @Test
    @DisplayName("형식 맞지 않는 JSON 오류 검증")
    void placeOrder_malformedBody_400() throws Exception {
        mockMvc.perform(post("/orders")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Disabled
    @Test
    @DisplayName("검증 오류 시 기본 오류 리턴")
    void placeOrder_validationFailure_400() throws Exception {
        mockMvc.perform(post("/orders")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requests":[{"rewardId":1,"quantity":-1}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errors[?(@.field == 'userId')]").exists())
                .andExpect(jsonPath("$.error.errors[?(@.field == 'requests[0].quantity')]").exists());
    }

    @Disabled
    @Test
    @DisplayName("주문 생성 시 중복 rewardId 오류는 실패 응답으로 반환한다")
    void placeOrder_duplicateRewardIds_400() throws Exception {
        when(orderApiService.placeOrder(any(PlaceOrderCommand.class), eq(1L)))
                .thenThrow(new IllegalArgumentException("Duplicate reward entries are not allowed. rewardId=1"));

        mockMvc.perform(post("/api/v1/orders")
                        .header(JwtHeaders.USER_ID, "1")
                        .header(JwtHeaders.USER_ROLE, UserRole.BACKER.name())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"projectId":10,"requests":[{"rewardId":1,"quantity":2,"expectedUnitPrice":10000},{"rewardId":1,"quantity":3,"expectedUnitPrice":10000}],"receiverName":"Receiver","receiverPhone":"010-0000-0000","shippingAddress":"Seoul","zipCode":"06236","expectedItemsAmount":50000,"expectedTotalAmount":50000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.message").value("Duplicate reward entries are not allowed. rewardId=1"))
                .andExpect(jsonPath("$.error.errors").doesNotExist());

        verify(orderApiService).placeOrder(any(PlaceOrderCommand.class), eq(1L));
        verifyNoMoreInteractions(orderApiService);
    }

    private OrderResult result(Long orderId, OrderStatus status) {
        return new OrderResult(orderId, status, BigDecimal.valueOf(20_000), BigDecimal.valueOf(3_000),
                BigDecimal.valueOf(23_000), "Receiver", "010-0000-0000", "Seoul", "06236",
                List.of(new OrderResult.Item(100L, "Reward A", BigDecimal.valueOf(20_000), 10L, 1L, 1,
                        BigDecimal.valueOf(20_000))));
    }
}
