package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderConsistencyView;
import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.ProductPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.application.port.dto.ProductSnapshot;
import com.growmighty.lectures.firstday.order.domain.Money;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 애플리케이션 서비스.
 *
 * <p>다른 도메인(product/payment)의 클래스를 직접 알지 않는다. 오직 주문이 스스로 정의한
 * {@link ProductPort} / {@link PaymentPort} 계약으로만 대화하고, 실제 통신은
 * infrastructure 의 HTTP 클라이언트가 담당한다. 이 경계 덕분에 order 는 컴파일 의존이
 * common 뿐이고, product/payment 와는 HTTP(JSON 계약)로만 연결된다.
 */
@Service
@RequiredArgsConstructor
public class OrderApiService {

    private final OrderRepository orderRepository;
    private final ProductPort productPort;
    private final PaymentPort paymentPort;

    @Transactional
    public OrderResult placeOrder(PlaceOrderCommand command) {
        List<OrderLine> lines = command.lines();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }

        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderLine line : lines) {
            ProductSnapshot product = productPort.getProduct(line.productId());
            if (!product.orderable()) {
                throw new IllegalStateException("현재 구매할 수 없는 상품입니다. productId=" + product.productId());
            }
            orderItems.add(OrderItem.create(product.name(), product.price(), product.productId(), line.quantity()));
            productPort.decreaseStock(line.productId(), line.quantity());
        }

        Order order = Order.create(command.userId(), orderItems);

        PaymentResult payment = paymentPort.pay(order.getTotalAmount().getValue());
        order.completePayment(payment.paymentId());

        return OrderResult.from(orderRepository.save(order));
    }

    @Transactional
    public OrderResult cancelOrder(Long orderId) {
        Order order = getOrder(orderId);
        order.cancel();

        for (OrderItem item : order.getItems()) {
            productPort.restoreStock(item.getProductId(), item.getQuantity());
        }
        if (order.getPaymentId() != null) {
            paymentPort.cancel(order.getPaymentId());
        }
        return OrderResult.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResult> getOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResult::from)
                .toList();
    }

    @Transactional
    public void changeItemPrice(Long orderId, Long orderItemId, BigDecimal newPrice) {
        getOrder(orderId).changeItemPrice(orderItemId, newPrice);
    }

    @Transactional
    public void changeItemQuantity(Long orderId, Long orderItemId, int newQuantity) {
        getOrder(orderId).changeItemQuantity(orderItemId, newQuantity);
    }

    @Transactional(readOnly = true)
    public OrderConsistencyView inspectOrder(Long orderId) {
        Order order = getOrder(orderId);
        Money storedTotal = order.getTotalAmount();
        Money recalculatedTotal = order.recalculatedTotal();
        return new OrderConsistencyView(
                orderId,
                storedTotal.getValue(),
                recalculatedTotal.getValue(),
                storedTotal.isSameAmount(recalculatedTotal));
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 주문입니다. orderId=" + orderId));
    }
}
