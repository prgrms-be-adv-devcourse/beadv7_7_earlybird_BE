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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
    private final List<OrderStatus> savedStatuses = new ArrayList<>();
    private long nextOrderId;

    @BeforeEach
    void setUp() {
        orderApiService = new OrderApiService(orderRepository, rewardPort, paymentPort);
        orders.clear();
        savedStatuses.clear();
        nextOrderId = 1L;
    }

    @Test
    @DisplayName("placeOrder requests payment only after all stock is reserved and returns the paid order")
    void placeOrder_requestsPaymentAfterAllStockReserved() {
        stubRepository();
        when(rewardPort.getReward(10L))
                .thenReturn(new RewardSnapshot(10L, 1L, "Reward A", BigDecimal.valueOf(10_000), 5, true));
        when(rewardPort.getReward(20L))
                .thenReturn(new RewardSnapshot(20L, 1L, "Reward B", BigDecimal.valueOf(15_000), 5, true));
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(50_000)));
        PlaceOrderCommand command = new PlaceOrderCommand(1L, 10L,
                List.of(
                        new OrderLine(10L, 2, BigDecimal.valueOf(10_000)),
                        new OrderLine(20L, 2, BigDecimal.valueOf(15_000))),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.valueOf(50_000), BigDecimal.valueOf(50_000));

        OrderResult result = orderApiService.placeOrder(command, 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(savedStatuses).containsExactly(
                OrderStatus.CREATED,
                OrderStatus.PAYMENT_REQUEST,
                OrderStatus.PAID);
        InOrder inOrder = inOrder(orderRepository, rewardPort, paymentPort);
        inOrder.verify(orderRepository).saveAndFlush(any(Order.class));
        inOrder.verify(rewardPort).decreaseStock(10L, 2);
        inOrder.verify(rewardPort).decreaseStock(20L, 2);
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(paymentPort).pay(1L, 1L, BigDecimal.valueOf(50_000));
    }

    @Test
    @DisplayName("placeOrder stops before payment and persists stock failure when stock reservation fails")
    void placeOrder_stockFailurePersistsFailedStateAndSkipsPayment() {
        stubRepository();
        stubReward();
        doThrow(new IllegalStateException("stock unavailable"))
                .when(rewardPort).decreaseStock(10L, 2);

        assertThatThrownBy(() -> orderApiService.placeOrder(command(), 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.STOCK_FAILED);
        assertThat(savedStatuses).containsExactly(OrderStatus.CREATED, OrderStatus.STOCK_FAILED);
        verify(paymentPort, never()).pay(any(), any(), any());
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("placeOrder persists payment failure and restores stock after confirmed payment failure")
    void placeOrder_paymentFailurePersistsFailedStateAndRestoresStock() {
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.failure(BigDecimal.valueOf(23_000)));

        OrderResult result = orderApiService.placeOrder(command(), 1L);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(savedStatuses).containsExactly(
                OrderStatus.CREATED,
                OrderStatus.PAYMENT_REQUEST,
                OrderStatus.PAYMENT_FAILED);
        verify(rewardPort).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("placeOrder persists processing state when payment result is uncertain")
    void placeOrder_unknownPaymentPersistsProcessingState() {
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.unknown(BigDecimal.valueOf(23_000)));

        OrderResult result = orderApiService.placeOrder(command(), 1L);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(orders.get(1L).getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(savedStatuses).containsExactly(
                OrderStatus.CREATED,
                OrderStatus.PAYMENT_REQUEST,
                OrderStatus.PAYMENT_PROCESSING);
        verify(rewardPort, never()).restoreStock(10L, 2);
    }

    @Test
    @DisplayName("placeOrder rejects invalid command before creating order or processing stock")
    void placeOrder_invalidCommandStopsBeforePersistenceAndRemoteProcessing() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, 10L, List.of(),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> orderApiService.placeOrder(command, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order must contain at least one item.");

        verifyNoInteractions(orderRepository, rewardPort, paymentPort);
        assertThat(orders).isEmpty();
        assertThat(savedStatuses).isEmpty();
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

    @Disabled
    @Test
    @DisplayName("주문 생성 시 같은 rewardId가 두 번 있으면 전체 요청을 거부한다")
    void placeOrder_duplicateRewardIds_rejectsBeforeProcessing() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, 10L,
                List.of(
                        new OrderLine(10L, 2, BigDecimal.valueOf(10_000)),
                        new OrderLine(10L, 3, BigDecimal.valueOf(10_000))),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.valueOf(50_000), BigDecimal.valueOf(50_000));

        assertThatThrownBy(() -> orderApiService.placeOrder(command, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate reward entries are not allowed. rewardId=10");

        verifyNoInteractions(orderRepository, rewardPort, paymentPort);
        assertThat(orders).isEmpty();
    }

    @Disabled
    @Test
    @DisplayName("주문 생성 시 같은 rewardId와 같은 수량이 두 번 있어도 전체 요청을 거부한다")
    void placeOrder_duplicateRewardIdsWithSameQuantity_rejectsBeforeProcessing() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, 10L,
                List.of(
                        new OrderLine(10L, 2, BigDecimal.valueOf(10_000)),
                        new OrderLine(10L, 2, BigDecimal.valueOf(10_000))),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.valueOf(40_000), BigDecimal.valueOf(43_000));

        assertThatThrownBy(() -> orderApiService.placeOrder(command, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate reward entries are not allowed. rewardId=10");

        verifyNoInteractions(orderRepository, rewardPort, paymentPort);
        assertThat(orders).isEmpty();
    }

    @Disabled
    @Test
    @DisplayName("주문 생성 시 여러 다른 rewardId는 각각 주문 항목으로 생성한다")
    void placeOrder_multipleUniqueRewards_createsEachOrderItem() {
        stubRepository();
        when(rewardPort.getReward(10L))
                .thenReturn(new RewardSnapshot(10L, 1L, "Reward A", BigDecimal.valueOf(10_000), 5, true));
        when(rewardPort.getReward(20L))
                .thenReturn(new RewardSnapshot(20L, 1L, "Reward B", BigDecimal.valueOf(15_000), 5, true));
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(50_000)));
        PlaceOrderCommand command = new PlaceOrderCommand(1L, 10L,
                List.of(
                        new OrderLine(10L, 2, BigDecimal.valueOf(10_000)),
                        new OrderLine(20L, 2, BigDecimal.valueOf(15_000))),
                "Receiver", "010-0000-0000", "Seoul", "06236",
                BigDecimal.valueOf(50_000), BigDecimal.valueOf(50_000));

        OrderResult result = orderApiService.placeOrder(command, 1L);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.orderItems())
                .extracting(OrderResult.Item::rewardId, OrderResult.Item::quantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, 2),
                        org.assertj.core.groups.Tuple.tuple(20L, 2));
        assertThat(orders.get(1L).getItems()).hasSize(2);
        verify(rewardPort).decreaseStock(10L, 2);
        verify(rewardPort).decreaseStock(20L, 2);
        verify(paymentPort).pay(1L, 1L, BigDecimal.valueOf(50_000));
    }

    @Disabled
    @Test
    @DisplayName("주문 생성 시 단일 rewardId는 기존 생성 흐름대로 성공한다")
    void placeOrder_singleReward_success() {
        stubRepository();
        stubReward();
        when(paymentPort.pay(any(), any(), any())).thenReturn(PaymentResult.success(99L, BigDecimal.valueOf(23_000)));

        OrderResult result = orderApiService.placeOrder(command(), 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.orderItems())
                .extracting(OrderResult.Item::rewardId, OrderResult.Item::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(10L, 2));
        verify(rewardPort).decreaseStock(10L, 2);
        verify(paymentPort).pay(1L, 1L, BigDecimal.valueOf(23_000));
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
        savedStatuses.add(order.getStatus());
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
