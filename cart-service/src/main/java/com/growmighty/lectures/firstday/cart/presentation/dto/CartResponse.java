package com.growmighty.lectures.firstday.cart.presentation.dto;

import com.growmighty.lectures.firstday.cart.application.dto.CartView;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long cartId,
        Long userId,
        int itemCount,
        List<ItemResponse> items,
        List<ProjectResponse> projects,
        BigDecimal totalItemsAmount,
        BigDecimal totalShippingFee,
        BigDecimal totalAmount
) {
    public record ItemResponse(Long rewardId, int quantity) {
    }

    public record ProjectResponse(
            Long projectId,
            String projectName,
            List<RewardResponse> rewards,
            BigDecimal itemsAmount,
            BigDecimal shippingFee,
            BigDecimal totalAmount
    ) {
    }

    public record RewardResponse(
            Long cartItemId,
            Long rewardId,
            String rewardName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {
    }

    public static CartResponse from(CartView view) {
        List<ItemResponse> items = view.items().stream()
                .map(line -> new ItemResponse(line.rewardId(), line.quantity()))
                .toList();
        List<ProjectResponse> projects = view.projects().stream()
                .map(project -> new ProjectResponse(
                        project.projectId(),
                        project.projectName(),
                        project.rewards().stream()
                                .map(reward -> new RewardResponse(
                                        reward.cartItemId(),
                                        reward.rewardId(),
                                        reward.rewardName(),
                                        reward.quantity(),
                                        reward.unitPrice(),
                                        reward.totalPrice()))
                                .toList(),
                        project.itemsAmount(),
                        project.shippingFee(),
                        project.totalAmount()))
                .toList();
        return new CartResponse(
                view.cartId(),
                view.userId(),
                items.size(),
                items,
                projects,
                view.totalItemsAmount(),
                view.totalShippingFee(),
                view.totalAmount());
    }
}
