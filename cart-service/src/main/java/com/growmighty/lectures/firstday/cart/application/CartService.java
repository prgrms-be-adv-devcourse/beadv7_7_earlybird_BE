package com.growmighty.lectures.firstday.cart.application;

import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemsCommand;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import com.growmighty.lectures.firstday.cart.application.dto.UpdateCartItemQuantitiesCommand;
import com.growmighty.lectures.firstday.cart.application.port.RewardPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.cart.domain.Cart;
import com.growmighty.lectures.firstday.cart.domain.CartItem;
import com.growmighty.lectures.firstday.cart.domain.CartRepository;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final RewardPort rewardPort;

    // Todo(예정, 미정) : 정합성 검증 관련 추가

    @Transactional
    public CartView addItems(AddCartItemsCommand command) {
        validateProjectId(command.projectId());
        List<CartLineCommand> items = toCartLineCommands(
                command.items(),
                item -> new CartLineCommand(item.rewardId(), item.quantity()));
        Cart cart = cartRepository.findByUserId(command.userId())
                .orElseGet(() -> Cart.create(command.userId()));
        Map<Long, RewardSnapshot> rewards = rewardPort.getRewards(rewardIds(items));

        validateRewards(command.projectId(), items, rewards);
        validateDistinctItemsLimit(cart, items);
        validateFinalQuantities(cart, items, rewards, true);

        items.forEach(item -> cart.addItem(item.rewardId(), item.quantity()));
        return toView(cartRepository.save(cart));
    }

    @Transactional
    public CartView updateItems(UpdateCartItemQuantitiesCommand command) {
        validateProjectId(command.projectId());
        List<CartLineCommand> items = toCartLineCommands(
                command.items(),
                item -> new CartLineCommand(item.rewardId(), item.quantity()));
        Cart cart = cartRepository.findByUserId(command.userId())
                .orElseGet(() -> Cart.create(command.userId()));
        Map<Long, RewardSnapshot> rewards = rewardPort.getRewards(rewardIds(items));

        validateRewards(command.projectId(), items, rewards);
        validateDistinctItemsLimit(cart, items);
        validateFinalQuantities(cart, items, rewards, false);

        items.forEach(item -> cart.setItemQuantity(item.rewardId(), item.quantity()));
        return toView(cartRepository.save(cart));
    }

    @Transactional
    public CartView removeItem(Long userId, Long rewardId) {
        Cart cart = getCartEntity(userId);
        cart.removeItem(rewardId);
        return toView(cart);
    }

    @Transactional
    public void clear(Long userId) {
        getCartEntity(userId).clear();
    }

    @Transactional
    public CartView getCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.create(userId)));
        return toView(cart);
    }

    private <T> List<CartLineCommand> toCartLineCommands(List<T> items, Function<T, CartLineCommand> mapper) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items are required.");
        }
        Set<Long> rewardIds = new HashSet<>();
        return items.stream()
                .map(item -> {
                    if (item == null) {
                        throw new IllegalArgumentException("Cart item is required.");
                    }
                    CartLineCommand command = mapper.apply(item);
                    if (command.rewardId() == null) {
                        throw new IllegalArgumentException("rewardId is required.");
                    }
                    if (command.quantity() == null || command.quantity() <= 0) {
                        throw new IllegalArgumentException("quantity must be positive. rewardId=" + command.rewardId());
                    }
                    if (!rewardIds.add(command.rewardId())) {
                        throw new IllegalArgumentException("Duplicate rewardId is not allowed. rewardId=" + command.rewardId());
                    }
                    return command;
                })
                .toList();
    }

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required.");
        }
    }

    private void validateRewards(Long projectId, List<CartLineCommand> items, Map<Long, RewardSnapshot> rewards) {
        for (CartLineCommand item : items) {
            RewardSnapshot reward = rewards.get(item.rewardId());
            if (reward == null) {
                throw new EntityNotFoundException("Reward not found. rewardId=" + item.rewardId());
            }
            if (reward.projectId() != null && !Objects.equals(projectId, reward.projectId())) {
                throw new IllegalArgumentException("Reward does not belong to the project. projectId="
                        + projectId + ", rewardId=" + item.rewardId());
            }
            if (!reward.orderable()) {
                throw new IllegalStateException("Reward is not orderable. rewardId=" + item.rewardId());
            }
        }
    }

    private void validateFinalQuantities(Cart cart, List<CartLineCommand> items,
                                         Map<Long, RewardSnapshot> rewards, boolean increment) {
        for (CartLineCommand item : items) {
            int finalQuantity = increment ? cart.quantityOf(item.rewardId()) + item.quantity() : item.quantity();
            if (finalQuantity > CartItem.MAX_QUANTITY) {
                throw new IllegalArgumentException("Cart item quantity cannot exceed " + CartItem.MAX_QUANTITY
                        + ". rewardId=" + item.rewardId());
            }
            RewardSnapshot reward = rewards.get(item.rewardId());
            if (reward.remainingQuantity() != null && finalQuantity > reward.remainingQuantity()) {
                throw new IllegalStateException("Reward stock is insufficient. rewardId=" + item.rewardId());
            }
        }
    }

    private void validateDistinctItemsLimit(Cart cart, List<CartLineCommand> items) {
        long newItemCount = items.stream()
                .filter(item -> !cart.containsReward(item.rewardId()))
                .count();
        if (cart.getItems().size() + newItemCount > Cart.MAX_DISTINCT_ITEMS) {
            throw new IllegalStateException("Cart cannot contain more than " + Cart.MAX_DISTINCT_ITEMS
                    + " distinct rewards.");
        }
    }

    private Set<Long> rewardIds(Collection<CartLineCommand> items) {
        return items.stream()
                .map(CartLineCommand::rewardId)
                .collect(Collectors.toSet());
    }

    private Set<Long> cartRewardIds(Cart cart) {
        return cart.getItems().stream()
                .map(CartItem::getRewardId)
                .collect(Collectors.toSet());
    }

    private CartView toView(Cart cart) {
        return CartView.from(cart, rewardPort.getRewards(cartRewardIds(cart)));
    }

    private Cart getCartEntity(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("장바구니가 비어 있습니다. userId=" + userId));
    }

    private record CartLineCommand(Long rewardId, Integer quantity) {
    }
}
