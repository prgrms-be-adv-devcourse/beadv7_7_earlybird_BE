package com.growmighty.lectures.firstday.order.presentation;

import com.growmighty.lectures.firstday.order.application.InternalOrderApiService;
import com.growmighty.lectures.firstday.order.presentation.dto.OrderVerification;
import com.growmighty.lectures.firstday.order.presentation.dto.PaymentStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/orders")
public class InternalOrderController {

    private final InternalOrderApiService internalOrderApiService;

    @PutMapping("/{orderId}/payment-status")
    public void updatePaymentStatus(@PathVariable Long orderId, @Valid @RequestBody PaymentStatusRequest request) {
        internalOrderApiService.applyPaymentStatus(orderId, request.status());
    }

    /** 해당 project의 order 존재 여부 리턴 */
    @GetMapping("/{projectId}/ordered-existence")
    public boolean hasOrderedReward(@PathVariable Long projectId) {
        return internalOrderApiService.hasOrderedReward(projectId);
    }

    /** project의 order 금액 합계 리턴 */
    @GetMapping("/{projectId}/funded-amount")
    public Optional<BigDecimal> getFundedAmount(@PathVariable Long projectId) {
        return internalOrderApiService.getFundedAmount(projectId);
    }

    /** 해당 project의 order 존재 여부 리턴 */
    @GetMapping("/purchase-verification")
    public OrderVerification getOrderedVerification(@RequestParam Long userId, @RequestParam Long rewardId) {
        return OrderVerification.from(internalOrderApiService.getOrderedVerification(userId, rewardId));
    }
}
