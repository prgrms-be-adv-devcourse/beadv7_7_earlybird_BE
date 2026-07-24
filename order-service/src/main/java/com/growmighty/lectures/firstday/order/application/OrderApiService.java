package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderConsistencyView;
import com.growmighty.lectures.firstday.order.application.dto.OrderInspectionView;
import com.growmighty.lectures.firstday.order.application.dto.OrderLine;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.PlaceOrderCommand;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort;
import com.growmighty.lectures.firstday.order.application.port.PaymentPort.RefundResult;
import com.growmighty.lectures.firstday.order.application.port.RewardPort;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.order.domain.Money;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderItem;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 주문 애플리케이션 서비스.
 *
 * <p>다른 도메인(reward/payment)의 클래스를 직접 알지 않는다. 오직 주문이 스스로 정의한
 * {@link RewardPort} / {@link PaymentPort} 계약으로만 대화하고, 실제 통신은
 * infrastructure 의 HTTP 클라이언트가 담당한다. 이 경계 덕분에 order 는 컴파일 의존이
 * common 뿐이고, project/payment 와는 HTTP(JSON 계약)로만 연결된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderApiService {

    private final OrderRepository orderRepository;
    private final RewardPort rewardPort;
    private final PaymentPort paymentPort;

    /**
     * 1. 주문 생성 요청 수신
     *    - 중복 요청 검증용 UUID 생성
     * 2. 주문 정합성 검증
          - 주문 생성 시 UUID 기반 중복 검사를 실시하지만 그 이후 단계에서도 필요 시 검증 추가
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
    public OrderResult placeOrder(PlaceOrderCommand command) {
        validateCommand(command);
        UUID orderId = resolveOrderId(command);
        if (orderRepository.findById(orderId).isPresent()) {
            log.info("duplicate order request returned existing order. orderId={}", orderId);
            return OrderResult.from(orderRepository.findById(orderId).orElseThrow());
        }

        Order order = createPendingOrder(command, orderId);
        try {
            orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            log.info("duplicate order insert race detected. orderId={}", orderId);
            return OrderResult.from(orderRepository.findById(orderId).orElseThrow());
        }

        try {
            reserveStock(order);
        } catch (RuntimeException e) {
            // TODO: Project service must provide a multi-reward atomic reservation endpoint to avoid partial deduction.
            order.markStockReservationFailed();
            orderRepository.save(order);
            throw e;
        }
        order.markPaymentRequested();
        orderRepository.save(order);

        PaymentResult payment = paymentPort.pay(order.getId(), order.getUserId(), order.getTotalAmount().getValue());
        applyPaymentResult(order, payment);
        return OrderResult.from(orderRepository.save(order));
    }

    // 결제 결과에 대한 처리
    public OrderResult applyPaymentResult(UUID orderId, PaymentResult paymentResult) {
        Order order = getOrderWithItems(orderId);
        applyPaymentResult(order, paymentResult);
        return OrderResult.from(orderRepository.save(order));
    }

    // 주문 취소
    public OrderResult cancelOrder(UUID orderId, Long userId) {
        Order order = getOrderWithItems(orderId);
        verifyOwner(order, userId);
        if (order.isCancelled()) {
            throw new IllegalStateException("Order is already cancelled. orderId=" + orderId);
        }

        verifyCancellationAllowedByProject(order);
        RefundResult refund = paymentPort.refund(order.getId(), order.getTotalAmount().getValue());
        if (refund.status() != PaymentResult.Status.SUCCESS) {
            throw new IllegalStateException("Refund failed or pending. orderId=" + orderId);
        }

        releaseStock(order);
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
    public OrderResult getOrderInfo(UUID orderId) {
        return OrderResult.from(getOrder(orderId));
    }

    @Transactional(readOnly = true)
    public OrderResult getOrderInfo(UUID orderId, Long userId) {
        Order order = getOrder(orderId);
        verifyOwner(order, userId);
        return OrderResult.from(order);
    }

    @Transactional(readOnly = true)
    public OrderConsistencyView inspectOrder(UUID orderId) {
        Order order = getOrder(orderId);
        Money storedTotal = order.getTotalAmount();
        Money recalculatedTotal = order.recalculatedTotal();
        return new OrderConsistencyView(
                orderId,
                storedTotal.getValue(),
                recalculatedTotal.getValue(),
                storedTotal.isSameAmount(recalculatedTotal));
    }

    @Transactional(readOnly = true)
    public OrderInspectionView placeOrderInspection(UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
        return OrderInspectionView.from(order);
    }

    @Transactional(readOnly = true)
    public boolean hasOrderedReward(Long projectId) {
        return orderRepository.existsByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getFundedAmount(Long projectId) {
        return orderRepository.getFundedAmount(projectId);
    }

    // 실질 주문 생성
    private Order createPendingOrder(PlaceOrderCommand command, UUID orderId) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderLine line : command.lines()) {
            RewardSnapshot reward = rewardPort.getReward(line.rewardId());
            validateRewardSnapshot(line, reward);
            orderItems.add(OrderItem.create(
                    reward.name(), reward.price(), reward.projectId(), reward.rewardId(), line.quantity()));
        }

        Order order = Order.create(orderId, command.userId(), orderItems,
                command.receiverName(), command.receiverPhone(), command.shippingAddress(), command.zipCode());
        validateAmounts(command, order);
        log.info("pending order created. orderId={}", order.getId());
        return order;
    }

    // 결제 결과에 대한 처리
    private void applyPaymentResult(Order order, PaymentResult payment) {
        if (order.getStatus() != OrderStatus.PAYMENT_REQUEST && order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            throw new IllegalStateException("Payment cannot be applied from status=" + order.getStatus());
        }

        if (payment.status() == PaymentResult.Status.SUCCESS) {
            order.markPaid(payment.paymentId());
            removeOrderedCartItems(order);
            log.info("payment succeeded. orderId={}", order.getId());
            return;
        }
        if (payment.status() == PaymentResult.Status.FAILURE) {
            order.markPaymentFailed();
            releaseStock(order);
            log.info("payment failed. orderId={}", order.getId());
            return;
        }

        if (order.getStatus() == OrderStatus.PAYMENT_REQUEST) {
            order.markPaymentProcessing();
        }
        log.warn("payment result is unknown. orderId={}", order.getId());
    }

    // 주문 형식 정합성 검사
    private void validateCommand(PlaceOrderCommand command) {
        if (command.lines() == null || command.lines().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item.");
        }
        Set<Long> rewardIds = new HashSet<>();
        for (OrderLine line : command.lines()) {
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
        if (reward.remainingQuantity() < line.quantity()) {
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

    private UUID resolveOrderId(PlaceOrderCommand command) {
        if (command.orderId() != null) {
            return command.orderId();
        }
        return UUID.randomUUID();
    }

    private List<Long> rewardIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getRewardId)
                .toList();
    }

    private List<Long> projectIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getProjectId)
                .distinct()
                .toList();
    }

    // 재고 확보 작업 로직 -> 일단 차감 후 다음 단계에서 복원
    // '재고 확보'를 '감소 처리'로 하는 것 자체가 확정은 아님
    // 경우에 따라서 reward 도메인이랑 협의 필요
    private void reserveStock(Order order) {
        // UPDATE rewards SET stock = stock - :quantity WHERE id = :rewardId AND stock >= :quantity.
        for (OrderItem item : order.getItems()) {
            rewardPort.decreaseStock(item.getRewardId(), item.getQuantity());
        }
        log.info("재고 확보 오류. orderId={}", order.getId());
    }

    // 재고 복원
    private void releaseStock(Order order) {
        // TODO(미정) : 정합성 검증 추가?
        for (OrderItem item : order.getItems()) {
            rewardPort.restoreStock(item.getRewardId(), item.getQuantity());
        }
        log.info("재고 복원 오류. orderId={}", order.getId());
    }

    // 최종 결제까지 성공 시 cart에서 삭제
    private void removeOrderedCartItems(Order order) {
        // TODO(예정) : cart 도메인에 해당 로직 추가
        log.info("temporary ordered cart item cleanup assumed successful. orderId={}, userId={}, rewardIds={}",
                order.getId(), order.getUserId(), rewardIds(order));
    }

    // 주문 취소 가능 여부 검증
    // 솔직하게 여기서 처리하는게 맞는지 확신은 없음
    // 중복 취소 검증 로직도 있어야 할거 같다고 생각되긴함
    private void verifyCancellationAllowedByProject(Order order) {
        // TODO(미정, 예정) : 주문 취소 가능 여부 검증
        log.info("temporary project cancellation policy allowed. orderId={}, projectIds={}", order.getId(), projectIds(order));
    }

    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }

    private Order getOrderWithItems(UUID orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }

    private void verifyOwner(Order order, Long userId) {
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new IllegalStateException("Order access denied. orderId=" + order.getId());
        }
    }
}
