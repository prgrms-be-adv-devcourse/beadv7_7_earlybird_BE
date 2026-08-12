package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderVerificationResult;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.dto.ProjectPaymentsView;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import com.growmighty.lectures.firstday.order.domain.OrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class InternalOrderApiService {

    private final OrderRepository orderRepository;
    private final OrderPaymentResultHandler paymentResultHandler;
    private final OrderPaidCompletionService paidCompletionService;

    @Autowired
    public InternalOrderApiService(OrderRepository orderRepository, OrderPaymentResultHandler paymentResultHandler,
                                   OrderPaidCompletionService paidCompletionService) {
        this.orderRepository = orderRepository;
        this.paymentResultHandler = paymentResultHandler;
        this.paidCompletionService = paidCompletionService;
    }

    InternalOrderApiService(OrderRepository orderRepository, OrderPaymentResultHandler paymentResultHandler) {
        this.orderRepository = orderRepository;
        this.paymentResultHandler = paymentResultHandler;
        this.paidCompletionService = null;
    }

    // 결제 결과에 대한 처리
    public OrderResult applyPaymentStatus(Long orderId, String paymentStatus) {
        PaymentResult paymentResult = switch (paymentStatus) {
            case "PAID" -> PaymentResult.success(null, null);
            case "FAILED", "CANCELLED" -> PaymentResult.failure(null);
            case "READY", "CONFIRMING" -> PaymentResult.pending(null);
            default -> throw new IllegalArgumentException("Unsupported payment status=" + paymentStatus);
        };
        return applyPaymentResult(orderId, paymentResult);
    }

    public OrderResult applyPaymentResult(Long orderId, PaymentResult paymentResult) {
        Order order = getOrderWithItems(orderId);
        boolean orderCompleted = paymentResultHandler.apply(order, paymentResult);
        boolean paidCleanupRequired = orderCompleted
                || (order.getStatus() == OrderStatus.PAID && paymentResult.status() == PaymentResult.Status.SUCCESS);
        if (paidCleanupRequired && paidCompletionService != null) {
            order = paidCompletionService.persistAndCleanup(order);
        } else {
            order = orderRepository.save(order);
            if (orderCompleted) {
                paymentResultHandler.removeOrderedCartItems(order);
            }
        }
        return OrderResult.from(order);
    }

    @Transactional(readOnly = true)
    public boolean hasOrderedReward(Long projectId) {
        return orderRepository.existsByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> getFundedAmount(Long projectId) {
        return orderRepository.getFundedAmount(projectId);
    }

    @Transactional(readOnly = true)
    public OrderVerificationResult getOrderedVerification(Long userId, Long rewardId) {
        return orderRepository.findPaidItem(userId, rewardId)
                .map(orderItem -> OrderVerificationResult.verified(orderItem.getName()))
                .orElseGet(OrderVerificationResult::unverified);
    }

    @Transactional(readOnly = true)
    public ProjectPaymentsView getProjectPayments(List<Long> requestedProjectIds) {
        List<Long> projectIds = requestedProjectIds.stream()
                .distinct()
                .sorted()
                .toList();
        Map<Long, List<ProjectPaymentsView.OrderPayment>> ordersByProjectId = new TreeMap<>();
        projectIds.forEach(projectId -> ordersByProjectId.put(projectId, new ArrayList<>()));

        List<Order> orders = orderRepository.findByProjectIdsAndStatusIn(
                projectIds, List.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        List<Long> missingOrderIds = orders.stream()
                .filter(order -> order.getPgOrderId() == null || order.getPgOrderId().isBlank())
                .map(Order::getId)
                .toList();
        if (!missingOrderIds.isEmpty()) {
            throw new IllegalStateException("Missing PG order IDs for settlement orders. orderIds=" + missingOrderIds);
        }

        orders.forEach(order -> ordersByProjectId.get(order.getProjectId())
                .add(new ProjectPaymentsView.OrderPayment(
                        order.getId(), order.getPgOrderId(), order.getTotalAmount().getValue(),
                        order.getStatus().name())));

        return toProjectPaymentsView(ordersByProjectId);
    }

    private ProjectPaymentsView toProjectPaymentsView(
            Map<Long, List<ProjectPaymentsView.OrderPayment>> ordersByProjectId) {
        return new ProjectPaymentsView(ordersByProjectId.entrySet().stream()
                .map(entry -> new ProjectPaymentsView.ProjectPayment(entry.getKey(), List.copyOf(entry.getValue())))
                .toList());
    }

    private Order getOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }
}
