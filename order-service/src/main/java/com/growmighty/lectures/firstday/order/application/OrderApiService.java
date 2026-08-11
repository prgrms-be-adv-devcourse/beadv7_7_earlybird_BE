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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 주문 애플리케이션 서비스.
 *
 * <p>다른 도메인(reward/payment)의 클래스를 직접 알지 않는다. 오직 주문이 스스로 정의한다.
 * {@link RewardPort} / {@link PaymentPort} 계약으로만 대화하고, 실제 통신은 infrastructure 의 HTTP 클라이언트가 담당한다.
 * 이 경계 덕분에 order 는 컴파일 의존이 common 뿐이고, project/payment 와는 HTTP(JSON 계약)로만 연결된다.
 */
@Slf4j
@Service
public class OrderApiService {

    private final OrderRepository orderRepository;
    private final RewardPort rewardPort;
    private final PaymentPort paymentPort;
    private final OrderRemoteCallExecutor remoteCalls;
    private final OrderStockHandler stockHandler;
    private final OrderPaymentResultHandler paymentResultHandler;

    @Autowired
    public OrderApiService(OrderRepository orderRepository, RewardPort rewardPort, PaymentPort paymentPort,
                           OrderRemoteCallExecutor remoteCalls, OrderStockHandler stockHandler,
                           OrderPaymentResultHandler paymentResultHandler) {
        this.orderRepository = orderRepository;
        this.rewardPort = rewardPort;
        this.paymentPort = paymentPort;
        this.remoteCalls = remoteCalls;
        this.stockHandler = stockHandler;
        this.paymentResultHandler = paymentResultHandler;
    }

    OrderApiService(OrderRepository orderRepository, RewardPort rewardPort, PaymentPort paymentPort) {
        this.orderRepository = orderRepository;
        this.rewardPort = rewardPort;
        this.paymentPort = paymentPort;
        this.remoteCalls = new OrderRemoteCallExecutor();
        this.stockHandler = new OrderStockHandler(rewardPort, remoteCalls);
        this.paymentResultHandler = new OrderPaymentResultHandler(stockHandler);
    }

    /**
     * 1. 주문 생성 요청 수신
     * 2. 주문 정합성 검증
          - 중복 생성 방지
     * 3. 주문 생성
     *    - OrderItem 스냅샷 저장
     *    - status = CREATED

     4. Project 서비스에 "재고 확보" 요청
     *    - 재고 검증과 확보를 원자적으로 수행
     * 4-1. 재고 확보 실패
     *    - status = STOCK_FAILED
     *    - 결제 호출하지 않음
     * 4-2. 재고 확보 성공
     *    - status = PAYMENT_REQUEST

     5. Payment 서비스 호출
     *    - 주문 상태, 금액, 중복 결제 여부 검증
     * 5-1. 결제 실패가 확정됨
     *    - status = PAYMENT_FAILED
     *    - 재고 복원 또는 예약 해제
     * 5-2. 결제 결과가 불확실함
     *    - status = PAYMENT_PROCESSING
     *    - 결제 결과 조회 또는 이벤트 대기
     * 5-3. 결제 성공
     *    - status = PAID
     *    - 재고 차감 확정
     *    - 주문한 장바구니 항목 삭제

     6. 사용자 취소
     *    - Payment 환불
     *    - 환불 성공 확인
     *    - 재고 복원
     *    - status = CANCELLED
     * */

    // 주문 생성 요청
    public OrderResult placeOrder(PlaceOrderCommand command, Long requesterId) {
        validateRequesterId(requesterId);
        command = new PlaceOrderCommand(requesterId, command.projectId(), command.lines(),
                command.receiverName(), command.receiverPhone(), command.shippingAddress(), command.zipCode(),
                command.expectedItemsAmount(), command.expectedTotalAmount(), command.orderIdempotencyKey());
        validateCommand(command);

        Optional<Order> existingOrder = orderRepository.findByUserIdAndOrderIdempotencyKey(
                requesterId, command.orderIdempotencyKey());
        if (existingOrder.isPresent()) {
            return OrderResult.from(existingOrder.get());
        }

        Order order = createPendingOrder(command);

        try {
            order = orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            return orderRepository.findByUserIdAndOrderIdempotencyKey(requesterId, command.orderIdempotencyKey())
                    .map(OrderResult::from)
                    .orElseThrow(() -> e);
        }

        List<OrderItem> confirmedItems = new ArrayList<>();
        try {
            stockHandler.reserveStock(order, confirmedItems);
        } catch (RuntimeException e) {
            if (remoteCalls.isTechnical(e)) {
                order.markStockPending();
                return OrderResult.from(orderRepository.save(order));
            }
            stockHandler.compensateStockFailure(order, confirmedItems);
            orderRepository.save(order);
            throw e;
        }

        order.markPaymentRequested();
        order = orderRepository.save(order);

        Order paymentOrder = order;
        PaymentResult payment;
        try {
            payment = remoteCalls.execute("payment-pay",
                    () -> paymentPort.pay(paymentOrder.getId(), paymentOrder.getUserId(),
                            paymentOrder.getTotalAmount().getValue()));
        } catch (RuntimeException e) {
            if (remoteCalls.isTechnical(e)) {
                order.markPaymentPending();
                return OrderResult.from(orderRepository.save(order));
            }
            paymentResultHandler.compensatePaymentFailure(order);
            orderRepository.save(order);
            throw e;
        }
        paymentResultHandler.apply(order, payment);

        return OrderResult.from(orderRepository.save(order));
    }

    // 주문 취소
    public OrderResult cancelOrder(Long orderId, Long requesterId) {
        validateRequesterId(requesterId);
        Order order = getOrderWithItems(orderId);
        verifyOwner(order, requesterId);
        if (order.isCancelled()) {
            throw new IllegalStateException("Order is already cancelled. orderId=" + orderId);
        }

        verifyCancellationAllowedByProject(order);
        RefundResult refund = paymentPort.refund(order.getId(), order.getTotalAmount().getValue());
        if (refund.status() != PaymentResult.Status.SUCCESS) {
            throw new IllegalStateException("Refund failed or pending. orderId=" + orderId);
        }

        stockHandler.releaseStock(order);
        order.cancel();
        log.info("order cancelled. orderId={}", orderId);
        return OrderResult.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResult> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResult::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResult getOrderInfo(Long orderId, Long requesterId) {
        validateRequesterId(requesterId);
        Order order = getOrderWithItems(orderId);
        verifyOwner(order, requesterId);
        return OrderResult.from(order);
    }

    // 실질 주문 생성
    private Order createPendingOrder(PlaceOrderCommand command) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderLine line : command.lines()) {
            RewardSnapshot reward = remoteCalls.execute("reward-get",
                    () -> rewardPort.getReward(line.rewardId()));
            validateRewardSnapshot(line, reward);
            orderItems.add(OrderItem.create(
                    reward.name(), reward.price(), reward.projectId(), reward.rewardId(), line.quantity()));
        }

        Long projectId = command.projectId() != null ? command.projectId() : orderItems.get(0).getProjectId();
        Order order = Order.create(null, command.userId(), projectId, orderItems,
                command.receiverName(), command.receiverPhone(), command.shippingAddress(), command.zipCode(),
                command.orderIdempotencyKey());
        validateAmounts(command, order);
        log.info("pending order created. orderId={}", order.getId());
        return order;
    }

    // 주문 형식 정합성 검사
    private void validateCommand(PlaceOrderCommand command) {
        if (command.orderIdempotencyKey() == null) {
            throw new IllegalArgumentException("orderIdempotencyKey is required.");
        }
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }
        Set<Long> rewardIds = new HashSet<>();
        for (OrderLine line : command.lines()) {
            if (line == null) {
                throw new IllegalArgumentException("Order item is required.");
            }
            if (line.rewardId() == null) {
                throw new IllegalArgumentException("rewardId is required.");
            }
            if (line.quantity() <= 0) {
                throw new IllegalArgumentException("Order quantity must be positive.");
            }
            if (line.expectedUnitPrice() == null || line.expectedUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Expected unit price must be non-negative.");
            }
            if (!rewardIds.add(line.rewardId())) {
                throw new IllegalArgumentException("Duplicate reward entries are not allowed. rewardId=" + line.rewardId());
            }
        }
    }

    // 정합성 세부 검사
    private void validateRewardSnapshot(OrderLine line, RewardSnapshot reward) {
        if (!reward.orderable()) {
            throw new IllegalStateException("Reward is not orderable. rewardId=" + reward.rewardId());
        }
        if (reward.remainingQuantity() != null && reward.remainingQuantity() < line.quantity()) {
            throw new IllegalStateException("Reward stock is insufficient. rewardId=" + reward.rewardId());
        }
        if (reward.price().compareTo(line.expectedUnitPrice()) != 0) {
            throw new IllegalArgumentException("Reward price changed. rewardId=" + reward.rewardId());
        }
    }

    // 금액 총합 일치 검사
    // TODO(예정,미정) : 단순 대조가 아니라 정밀 검사 할거면 주문의 reward id 기반으로 db상에서 reward 금액과 수량 기반으로 대조해야됨
    private void validateAmounts(PlaceOrderCommand command, Order order) {
        assertSameAmount(command.expectedItemsAmount(), order.getItemsAmount().getValue(), "itemsAmount");
        assertSameAmount(command.expectedTotalAmount(), order.getTotalAmount().getValue(), "totalAmount");
    }

    private void assertSameAmount(BigDecimal expected, BigDecimal actual, String fieldName) {
        if (expected == null || expected.compareTo(actual) != 0) {
            throw new IllegalArgumentException("Invalid order amount. field=" + fieldName);
        }
    }

    private List<Long> projectIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getProjectId)
                .distinct()
                .toList();
    }

    // 주문 취소 가능 여부 검증
    // 솔직하게 여기서 처리하는게 맞는지 확신은 없음
    // 중복 취소 검증 로직도 있어야 할거 같다고 생각되긴함
    private void verifyCancellationAllowedByProject(Order order) {
        // TODO(미정, 예정) : 주문 취소 가능 여부 검증
        log.info("temporary project cancellation policy allowed. orderId={}, projectIds={}", order.getId(), projectIds(order));
    }

    private Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }

    private Order getOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }

    private void verifyOwner(Order order, Long userId) {
        if (userId == null || !order.getUserId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Order access denied. orderId=" + order.getId());
        }
    }

    private void validateRequesterId(Long requesterId) {
        if (requesterId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Order access denied.");
        }
    }

}
