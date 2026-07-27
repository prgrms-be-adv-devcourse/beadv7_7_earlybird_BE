package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort.RefundResult;
import com.growmighty.lectures.firstday.order.application.port.RewardPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApiServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RewardPort rewardPort;
    @Mock
    private PaymentPort paymentPort;

    private OrderApiService orderApiService;
    private final Map<UUID, Order> orders = new HashMap<>();

    @BeforeEach
    void setUp() {
        orderApiService = new OrderApiService(orderRepository, rewardPort, paymentPort);
    }

    @Test
    @DisplayName("order 과정 전체 성공")
    void placeOrder_success() {
        UUID orderId = UUID.randomUUID();
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(23000)));

        OrderResult result = orderApiService.placeOrder(command(orderId), 1L);

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(rewardPort, times(1)).decreaseStock(10L, 2);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("재고 확보 및 차감 실패")
    void placeOrder_stockFailure() {
        UUID orderId = UUID.randomUUID();
        stubRepository();
        stubReward();
        doThrow(new IllegalStateException("stock unavailable"))
                .when(rewardPort).decreaseStock(10L, 2);

        assertThatThrownBy(() -> orderApiService.placeOrder(command(orderId), 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.get(orderId).getStatus()).isEqualTo(OrderStatus.STOCK_FAILED);
        verify(paymentPort, never()).pay(any(), any(), any());
    }

    @Test
    @DisplayName("결제 실패 후 상태 전환및 재고 복원")
    void placeOrder_paymentFailure() {
        UUID orderId = UUID.randomUUID();
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.failure(BigDecimal.valueOf(23000)));

        OrderResult result = orderApiService.placeOrder(command(orderId), 1L);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(rewardPort).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("결제 지연 후 최종적으로는 성공")
    void delayedPaymentSuccess() {
        UUID orderId = UUID.randomUUID();
        Order order = processingOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.applyPaymentResult(orderId, PaymentResult.success(99L, BigDecimal.valueOf(23000)));

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(rewardPort, never()).decreaseStock(10L, 2);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("결제 지연 후 실패 및 재고 복원")
    void delayedPaymentFailure() {
        UUID orderId = UUID.randomUUID();
        Order order = processingOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.applyPaymentResult(orderId, PaymentResult.failure(BigDecimal.valueOf(23000)));

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(rewardPort).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("주문 취소 전체 과정 성공 테스트")
    void cancelOrder_success() {
        UUID orderId = UUID.randomUUID();
        Order order = paidOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(paymentPort.refund(orderId, BigDecimal.valueOf(23000)))
                .thenReturn(RefundResult.success(BigDecimal.valueOf(23000), "refund-1"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.cancelOrder(orderId, 1L);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(paymentPort).refund(orderId, BigDecimal.valueOf(23000));
        verify(rewardPort).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("주문 자체는 취소, 환불은 실패")
    void cancelOrder_refundFailure() {
        UUID orderId = UUID.randomUUID();
        Order order = paidOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(paymentPort.refund(orderId, BigDecimal.valueOf(23000)))
                .thenReturn(new RefundResult(PaymentResult.Status.FAILURE, BigDecimal.valueOf(23000), null));

        assertThatThrownBy(() -> orderApiService.cancelOrder(orderId, 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("다른 사용자의 주문 상세는 조회할 수 없다")
    void getOrderInfo_otherUser_forbidden() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder(orderId)));

        assertThatThrownBy(() -> orderApiService.getOrderInfo(orderId, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 사용자의 주문은 취소할 수 없다")
    void cancelOrder_otherUser_forbidden() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder(orderId)));

        assertThatThrownBy(() -> orderApiService.cancelOrder(orderId, 2L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(paymentPort);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("주문 생성은 requesterId를 주문 소유자로 사용한다")
    void placeOrder_usesRequesterIdAsOwner() {
        UUID orderId = UUID.randomUUID();
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(23000)));

        OrderResult result = orderApiService.placeOrder(command(orderId, 999L), 1L);

        assertThat(result.id()).isEqualTo(orderId);
        assertThat(orders.get(orderId).getUserId()).isEqualTo(1L);
        verify(paymentPort).pay(orderId, 1L, BigDecimal.valueOf(23000));
    }

    private void stubRepository() {
        when(orderRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(orders.get(invocation.getArgument(0))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            orders.put(order.getId(), order);
            return order;
        });
    }

    private void stubReward() {
        when(rewardPort.getReward(10L))
                .thenReturn(new RewardSnapshot(10L, 1L, "Reward A", BigDecimal.valueOf(10_000), 5, true));
    }

    private PlaceOrderCommand command(UUID orderId) {
        return command(orderId, 1L);
    }

    private PlaceOrderCommand command(UUID orderId, Long userId) {
        return new PlaceOrderCommand(orderId, userId, List.of(new OrderLine(10L, 2, BigDecimal.valueOf(10_000))),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.valueOf(20_000), BigDecimal.valueOf(23_000));
    }

    private Order processingOrder(UUID orderId) {
        Order order = Order.create(orderId, 1L,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 1L, 10L, 2)),
                "Receiver", "010-0000-0000", "Seoul", "06236");
        order.markPaymentRequested();
        order.markPaymentProcessing();
        return order;
    }

    private Order paidOrder(UUID orderId) {
        Order order = Order.create(orderId, 1L,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 1L, 10L, 2)),
                "Receiver", "010-0000-0000", "Seoul", "06236");
        order.markPaymentRequested();
        order.markPaid(99L);
        return order;
    }
}
