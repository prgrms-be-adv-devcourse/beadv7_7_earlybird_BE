package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.ProductPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.application.port.dto.ProductSnapshot;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 주문 서비스는 이제 다른 도메인의 서비스 빈이 아니라 자기 소유의 Port(계약)에만 의존한다.
 * 그래서 테스트도 ProductService/PaymentService 대신 ProductPort/PaymentPort 를 목킹한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderApiServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductPort productPort;
    @Mock
    private PaymentPort paymentPort;

    @InjectMocks
    private OrderApiService orderApiService;

    @Test
    @DisplayName("주문 생성: 재고 차감·결제 승인을 호출하고 결제 ID를 주문에 연결한다")
    void placeOrder_orchestratesStockAndPayment() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, List.of(new OrderLine(10L, 2)));
        when(productPort.getProduct(10L))
                .thenReturn(new ProductSnapshot(10L, "원목 식탁", BigDecimal.valueOf(10_000), 5, true));
        when(paymentPort.pay(any()))
                .thenReturn(new PaymentResult(99L, BigDecimal.valueOf(23_000), "PAID"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResult result = orderApiService.placeOrder(command);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.totalAmount()).isEqualByComparingTo("23000");
        verify(productPort).decreaseStock(10L, 2);

        ArgumentCaptor<BigDecimal> paidAmount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(paymentPort).pay(paidAmount.capture());
        assertThat(paidAmount.getValue()).isEqualByComparingTo("23000");

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getPaymentId()).isEqualTo(99L);
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("주문 생성: 라인이 비어 있으면 재고/결제를 건드리지 않고 예외가 발생한다")
    void placeOrder_emptyLines_throws() {
        PlaceOrderCommand command = new PlaceOrderCommand(1L, List.of());

        assertThatThrownBy(() -> orderApiService.placeOrder(command))
                .isInstanceOf(IllegalArgumentException.class);

        verify(productPort, never()).decreaseStock(any(), ArgumentMatchers.anyInt());
        verify(paymentPort, never()).pay(any());
    }

    @Test
    @DisplayName("주문 취소: 재고를 복원하고 결제를 취소하며 상태가 CANCELLED로 전이된다")
    void cancelOrder_restoresStockAndRefunds() {
        Order order = Order.create(1L, List.of(OrderItem.create("원목 식탁", BigDecimal.valueOf(10_000), 10L, 2)));
        order.completePayment(99L);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResult result = orderApiService.cancelOrder(5L);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productPort).restoreStock(10L, 2);
        verify(paymentPort).cancel(99L);
    }

    @Test
    @DisplayName("주문 취소: 존재하지 않는 주문이면 EntityNotFoundException이 발생한다")
    void cancelOrder_notFound_throws() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderApiService.cancelOrder(404L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
