package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
    private final Map<Long, Order> orders = new HashMap<>();
    private long nextOrderId;

    @BeforeEach
    void setUp() {
        orderApiService = new OrderApiService(orderRepository, rewardPort, paymentPort);
        orders.clear();
        nextOrderId = 1L;
    }

    @Disabled
    @Test
    @DisplayName("order 과정 전체 성공")
    void placeOrder_success() {
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(23000)));

        OrderResult result = orderApiService.placeOrder(command(), 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(orders.get(1L).getProjectId()).isEqualTo(10L);
        verify(rewardPort, times(1)).decreaseStock(10L, 2);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Disabled
    @Test
    @DisplayName("재고 확보 및 차감 실패")
    void placeOrder_stockFailure() {
        stubRepository();
        stubReward();
        doThrow(new IllegalStateException("stock unavailable"))
                .when(rewardPort).decreaseStock(10L, 2);

        assertThatThrownBy(() -> orderApiService.placeOrder(command(), 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.STOCK_FAILED);
        verify(paymentPort, never()).pay(any(), any(), any());
    }

    @Disabled
    @Test
    @DisplayName("결제 실패 후 상태 전환및 재고 복원")
    void placeOrder_paymentFailure() {
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.failure(BigDecimal.valueOf(23000)));

        OrderResult result = orderApiService.placeOrder(command(), 1L);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(rewardPort).restoreStock(10L, 2);
    }

    @Disabled
    @Test
    @DisplayName("결제 지연 후 최종적으로는 성공")
    void delayedPaymentSuccess() {
        Long orderId = 1L;
        Order order = processingOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.applyPaymentResult(orderId, PaymentResult.success(99L, BigDecimal.valueOf(23000)));

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(rewardPort, never()).decreaseStock(10L, 2);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Disabled
    @Test
    @DisplayName("결제 지연 후 실패 및 재고 복원")
    void delayedPaymentFailure() {
        Long orderId = 1L;
        Order order = processingOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.applyPaymentResult(orderId, PaymentResult.failure(BigDecimal.valueOf(23000)));

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(rewardPort).restoreStock(10L, 2);
    }

    @Disabled
    @Test
    @DisplayName("주문 취소 전체 과정 성공 테스트")
    void cancelOrder_success() {
        Long orderId = 1L;
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

    @Disabled
    @Test
    @DisplayName("주문 자체는 취소, 환불은 실패")
    void cancelOrder_refundFailure() {
        Long orderId = 1L;
        Order order = paidOrder(orderId);
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(paymentPort.refund(orderId, BigDecimal.valueOf(23000)))
                .thenReturn(new RefundResult(PaymentResult.Status.FAILURE, BigDecimal.valueOf(23000), null));

        assertThatThrownBy(() -> orderApiService.cancelOrder(orderId, 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Disabled
    @Test
    @DisplayName("다른 사용자의 주문 상세는 조회할 수 없다")
    void getOrderInfo_otherUser_forbidden() {
        Long orderId = 1L;
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder(orderId)));

        assertThatThrownBy(() -> orderApiService.getOrderInfo(orderId, 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Disabled
    @Test
    @DisplayName("다른 사용자의 주문은 취소할 수 없다")
    void cancelOrder_otherUser_forbidden() {
        Long orderId = 1L;
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder(orderId)));

        assertThatThrownBy(() -> orderApiService.cancelOrder(orderId, 2L))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(paymentPort);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Disabled
    @Test
    @DisplayName("주문 생성은 requesterId를 주문 소유자로 사용한다")
    void placeOrder_usesRequesterIdAsOwner() {
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(23000)));

        OrderResult result = orderApiService.placeOrder(command(999L), 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(orders.get(1L).getUserId()).isEqualTo(1L);
        verify(paymentPort).pay(1L, 1L, BigDecimal.valueOf(23000));
    }

    private void stubRepository() {
        lenient().when(orderRepository.findById(any(Long.class))).thenAnswer(invocation ->
                Optional.ofNullable(orders.get(invocation.getArgument(0))));
        lenient().when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> saveOrder(invocation.getArgument(0)));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> saveOrder(invocation.getArgument(0)));
    }

    private Order saveOrder(Order order) {
        if (order.getId() == null) {
            setOrderId(order, nextOrderId++);
        }
        orders.put(order.getId(), order);
        return order;
    }

    private void setOrderId(Order order, Long id) {
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubReward() {
        when(rewardPort.getReward(10L))
                .thenReturn(new RewardSnapshot(10L, 1L, "Reward A", BigDecimal.valueOf(10_000), 5, true));
    }

    private PlaceOrderCommand command() {
        return command(1L);
    }

    private PlaceOrderCommand command(Long userId) {
        return new PlaceOrderCommand(userId, 10L, List.of(new OrderLine(10L, 2, BigDecimal.valueOf(10_000))),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.valueOf(20_000), BigDecimal.valueOf(23_000));
    }

    private Order processingOrder(Long orderId) {
        Order order = Order.create(orderId, 1L, 1L,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 1L, 10L, 2)),
                "Receiver", "010-0000-0000", "Seoul", "06236");
        order.markPaymentRequested();
        order.markPaymentProcessing();
        return order;
    }

    private Order paidOrder(Long orderId) {
        Order order = Order.create(orderId, 1L, 1L,
                List.of(OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 1L, 10L, 2)),
                "Receiver", "010-0000-0000", "Seoul", "06236");
        order.markPaymentRequested();
        order.markPaid();
        return order;
    }
}
