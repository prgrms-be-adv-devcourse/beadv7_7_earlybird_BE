package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.order.application.dto.OrderVerificationResult;
import com.growmighty.lectures.firstday.order.application.dto.OrderResult;
import com.growmighty.lectures.firstday.order.application.port.dto.PaymentResult;
import com.growmighty.lectures.firstday.order.domain.Order;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class InternalOrderApiService {

    private final OrderRepository orderRepository;
    private final OrderPaymentResultHandler paymentResultHandler;

    @Autowired
    public InternalOrderApiService(OrderRepository orderRepository, OrderPaymentResultHandler paymentResultHandler) {
        this.orderRepository = orderRepository;
        this.paymentResultHandler = paymentResultHandler;
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
        paymentResultHandler.apply(order, paymentResult);
        return OrderResult.from(orderRepository.save(order));
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

    private Order getOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found. orderId=" + orderId));
    }
}
