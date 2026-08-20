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
import java.time.LocalDate;
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
    private final OrderCancellationPersistenceService cancellationPersistenceService;
    private final OrderCancellationCompletionService cancellationCompletionService;

    @Autowired
    public InternalOrderApiService(OrderRepository orderRepository, OrderPaymentResultHandler paymentResultHandler,
                                   OrderPaidCompletionService paidCompletionService,
                                   OrderCancellationPersistenceService cancellationPersistenceService,
                                   OrderCancellationCompletionService cancellationCompletionService) {
        this.orderRepository = orderRepository;
        this.paymentResultHandler = paymentResultHandler;
        this.paidCompletionService = paidCompletionService;
        this.cancellationPersistenceService = cancellationPersistenceService;
        this.cancellationCompletionService = cancellationCompletionService;
    }

    InternalOrderApiService(OrderRepository orderRepository, OrderPaymentResultHandler paymentResultHandler) {
        this.orderRepository = orderRepository;
        this.paymentResultHandler = paymentResultHandler;
        this.paidCompletionService = null;
        this.cancellationPersistenceService = null;
        this.cancellationCompletionService = null;
    }

    // 결제 결과에 대한 처리
    public OrderResult applyPaymentStatus(Long orderId, String paymentStatus) {
        return applyPaymentStatus(orderId, null, paymentStatus);
    }

    public OrderResult applyPaymentStatus(Long orderId, String pgOrderId, String paymentStatus) {
        if ("CANCELLED".equals(paymentStatus)) {
            return applyPaymentCancellation(orderId, pgOrderId);
        }
        PaymentResult paymentResult = switch (paymentStatus) {
            case "PAID" -> PaymentResult.success(null, pgOrderId, null);
            case "FAILED" -> PaymentResult.failure(pgOrderId, null);
            case "READY", "CONFIRMING" -> PaymentResult.pending(pgOrderId, null);
            default -> throw new IllegalArgumentException("Unsupported payment status=" + paymentStatus);
        };
        return applyPaymentResult(orderId, paymentResult);
    }

    private OrderResult applyPaymentCancellation(Long orderId, String pgOrderId) {
        if (cancellationPersistenceService == null) {
            throw new IllegalStateException("Order cancellation finalization is unavailable. orderId=" + orderId);
        }
        Order cancelledOrder = cancellationPersistenceService.finalizeCancellation(orderId, pgOrderId);
        if (cancellationCompletionService != null) {
            cancellationCompletionService.complete(cancelledOrder);
        }
        return OrderResult.from(cancelledOrder);
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
    public ProjectPaymentsView getProjectPayments(int projectMonth) {
        int projectYear = LocalDate.now().getYear();

        Map<Long, List<ProjectPaymentsView.OrderPayment>> ordersByProjectId = new TreeMap<>();

        List<Order> orders = orderRepository.findByProjectYearAndMonthAndStatusIn(
            projectYear,
            projectMonth,
            List.of(OrderStatus.PAID, OrderStatus.CANCELLED));

        List<Long> missingOrderIds = orders.stream()
            .filter(order -> order.getPgOrderId() == null || order.getPgOrderId().isBlank())
            .map(Order::getId)
            .toList();

        if (!missingOrderIds.isEmpty()) {
            throw new IllegalStateException(
                "Missing PG order IDs for settlement orders. orderIds=" + missingOrderIds);
        }

        orders.forEach(order -> ordersByProjectId
            .computeIfAbsent(order.getProjectId(), key -> new ArrayList<>())
            .add(new ProjectPaymentsView.OrderPayment(
                order.getId(),
                order.getPgOrderId(),
                order.getTotalAmount().getValue(),
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
