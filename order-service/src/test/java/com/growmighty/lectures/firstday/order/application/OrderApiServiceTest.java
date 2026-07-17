package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderInspectionView;
import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.RewardPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import com.growmighty.lectures.firstday.order.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 주문 서비스는 이제 다른 도메인의 서비스 빈이 아니라 자기 소유의 Port(계약)에만 의존한다.
 * 그래서 테스트도 RewardService/PaymentService 대신 RewardPort/PaymentPort 를 목킹한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderApiServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RewardPort rewardPort;
    @Mock
    private PaymentPort paymentPort;

    @InjectMocks
    private OrderApiService orderApiService;

    @Test
    @DisplayName("주문 생성: 재고 차감·결제 승인을 호출하고 결제 ID를 주문에 연결한다")
    void placeOrder_orchestratesStockAndPayment() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, List.of(new OrderLine(10L, 2)),
                "김하나한", "010-0000-0000", "서울시 강남구", "06236");
        when(rewardPort.getReward(10L))
                .thenReturn(new RewardSnapshot(10L, 1L, "원목 식탁", BigDecimal.valueOf(10_000), 5, true));
        when(paymentPort.pay(any(), any()))
                .thenReturn(new PaymentResult(99L, BigDecimal.valueOf(23_000), "PAID"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.placeOrder(command);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.totalAmount()).isEqualByComparingTo("23000");
        verify(rewardPort).decreaseStock(10L, 2);

        ArgumentCaptor<BigDecimal> paidAmount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentPort).pay(any(), paidAmount.capture());
        assertThat(paidAmount.getValue()).isEqualByComparingTo("23000");

        // 결제 호출 전(주문 id 확보용)과 결제 완료 후, 총 2번 저장된다.
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getPaymentId()).isEqualTo(99L);
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("주문 생성: 라인이 비어 있으면 재고/결제를 건드리지 않고 예외가 발생한다")
    void placeOrder_emptyLines_throws() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, List.of(),
                "김하나한", "010-0000-0000", "서울시 강남구", "06236");

        assertThatThrownBy(() -> orderApiService.placeOrder(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(rewardPort, never()).decreaseStock(any(), ArgumentMatchers.anyInt());
        verify(paymentPort, never()).pay(any(), any());
    }

    @Test
    @DisplayName("주문 취소: 재고를 복원하고 결제를 취소하며 상태가 CANCELLED로 전이된다")
    void cancelOrder_restoresStockAndRefunds() {
        Order order = Order.create(1L, List.of(OrderItem.create("원목 식탁", BigDecimal.valueOf(10_000), 1L, 10L, 2)),
                "김하나한", "010-0000-0000", "서울시 강남구", "06236");
        order.completePayment(99L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResult result = orderApiService.cancelOrder(5L);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(rewardPort).restoreStock(10L, 2);
        verify(paymentPort).cancel(99L);
    }

    @Test
    @DisplayName("주문 취소: 존재하지 않는 주문이면 EntityNotFoundException이 발생한다")
    void cancelOrder_notFound_throws() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderApiService.cancelOrder(404L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("placeOrderInspection returns stored order and item details")
    void placeOrderInspection_returnsStoredOrderAndItems() {
        OrderItem first = OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 100L, 10L, 2);
        OrderItem second = OrderItem.create("Reward B", BigDecimal.valueOf(5_000), 101L, 11L, 3);
        Order order = Order.create(7L, List.of(first, second),
                "Receiver", "010-0000-0000", "Seoul", "06236");
        setId(order, 55L);
        setId(first, 1L);
        setId(second, 2L);
        when(orderRepository.findByIdWithItems(55L)).thenReturn(Optional.of(order));

        OrderInspectionView result = orderApiService.placeOrderInspection(55L);

        assertThat(result.orderId()).isEqualTo(55L);
        assertThat(result.userId()).isEqualTo(7L);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.itemsAmount()).isEqualByComparingTo("35000");
        assertThat(result.paymentAmount()).isEqualByComparingTo("38000");
        assertThat(result.totalAmount()).isEqualByComparingTo("38000");
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).rewardId()).isEqualTo(10L);
        assertThat(result.items().get(0).quantity()).isEqualTo(2);
        assertThat(result.items().get(0).unitAmount()).isEqualByComparingTo("10000");
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("20000");
        assertThat(result.items().get(1).rewardId()).isEqualTo(11L);
        assertThat(result.items().get(1).amount()).isEqualByComparingTo("15000");
        verifyNoInteractions(rewardPort, paymentPort);
    }

    @Test
    @DisplayName("placeOrderInspection uses stored total and payment amount")
    void placeOrderInspection_usesStoredTotalAmount() {
        OrderItem item = OrderItem.create("Reward A", BigDecimal.valueOf(10_000), 100L, 10L, 2);
        Order order = Order.create(7L, List.of(item),
                "Receiver", "010-0000-0000", "Seoul", "06236");
        setId(order, 55L);
        ReflectionTestUtils.setField(item, "price", Money.from(BigDecimal.valueOf(99_999)));
        when(orderRepository.findByIdWithItems(55L)).thenReturn(Optional.of(order));

        OrderInspectionView result = orderApiService.placeOrderInspection(55L);

        assertThat(result.totalAmount()).isEqualByComparingTo("23000");
        assertThat(result.paymentAmount()).isEqualByComparingTo("23000");
    }

    @Test
    @DisplayName("placeOrderInspection throws EntityNotFoundException when order is missing")
    void placeOrderInspection_notFound_throws() {
        when(orderRepository.findByIdWithItems(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderApiService.placeOrderInspection(404L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private void setId(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
    }
}
