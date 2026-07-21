package com.growmighty.lectures.firstday.cart.application.dto;

import com.growmighty.lectures.firstday.cart.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.cart.domain.Cart;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CartView(
        Long cartId,
        Long userId,
        List<Line> items,
        List<ProjectGroup> projects,
        BigDecimal totalItemsAmount,
        BigDecimal totalShippingFee,
        BigDecimal totalAmount
) {
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = BigDecimal.valueOf(50_000);
    private static final BigDecimal BASE_SHIPPING_FEE = BigDecimal.valueOf(3_000);

    public record Line(Long rewardId, int quantity) {
    }

    public record ProjectGroup(
            Long projectId,
            String projectName,
            List<RewardLine> rewards,
            BigDecimal itemsAmount,
            BigDecimal shippingFee,
            BigDecimal totalAmount
    ) {
    }

    public record RewardLine(
            Long cartItemId,
            Long rewardId,
            String rewardName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {
    }

    public static CartView from(Cart cart) {
        return from(cart, Map.of());
    }

    public static CartView from(Cart cart, Map<Long, RewardSnapshot> rewards) {
        List<Line> lines = cart.getItems().stream()
                .map(item -> new Line(item.getRewardId(), item.getQuantity()))
                .toList();

        Map<Long, List<RewardLineWithProject>> grouped = new LinkedHashMap<>();
        cart.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getRewardId()))
                .map(item -> toRewardLine(item.getId(), item.getRewardId(), item.getQuantity(), rewards.get(item.getRewardId())))
                .forEach(line -> grouped.computeIfAbsent(line.projectId(), ignored -> new java.util.ArrayList<>()).add(line));

        List<ProjectGroup> projects = grouped.entrySet().stream()
                .map(entry -> toProjectGroup(entry.getKey(), entry.getValue()))
                .toList();
        BigDecimal totalItemsAmount = projects.stream()
                .map(ProjectGroup::itemsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalShippingFee = projects.stream()
                .map(ProjectGroup::shippingFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(
                cart.getId(),
                cart.getUserId(),
                lines,
                projects,
                totalItemsAmount,
                totalShippingFee,
                totalItemsAmount.add(totalShippingFee));
    }

    private static RewardLineWithProject toRewardLine(Long cartItemId, Long rewardId, int quantity, RewardSnapshot reward) {
        BigDecimal unitPrice = reward == null || reward.price() == null ? BigDecimal.ZERO : reward.price();
        return new RewardLineWithProject(
                reward == null ? null : reward.projectId(),
                reward == null ? null : reward.projectName(),
                cartItemId,
                rewardId,
                reward == null ? null : reward.rewardName(),
                quantity,
                unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    private static ProjectGroup toProjectGroup(Long projectId, List<RewardLineWithProject> lines) {
        List<RewardLine> rewards = lines.stream()
                .map(line -> new RewardLine(
                        line.cartItemId(),
                        line.rewardId(),
                        line.rewardName(),
                        line.quantity(),
                        line.unitPrice(),
                        line.totalPrice()))
                .toList();
        BigDecimal itemsAmount = rewards.stream()
                .map(RewardLine::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shippingFee = itemsAmount.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : BASE_SHIPPING_FEE;
        return new ProjectGroup(
                projectId,
                lines.isEmpty() ? null : lines.get(0).projectName(),
                rewards,
                itemsAmount,
                shippingFee,
                itemsAmount.add(shippingFee));
    }

    private record RewardLineWithProject(
            Long projectId,
            String projectName,
            Long cartItemId,
            Long rewardId,
            String rewardName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {
    }
}
