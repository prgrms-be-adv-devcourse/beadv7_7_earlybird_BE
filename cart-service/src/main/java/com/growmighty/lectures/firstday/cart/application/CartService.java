package com.growmighty.lectures.firstday.cart.application;

import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemCommand;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import com.growmighty.lectures.firstday.cart.domain.Cart;
import com.growmighty.lectures.firstday.cart.domain.CartRepository;
import com.growmighty.lectures.firstday.cart.application.port.RewardPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.RewardSnapshot;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final RewardPort rewardPort;

    @Transactional
    public CartView addItem(AddCartItemCommand command) {
        RewardSnapshot reward = rewardPort.getReward(command.rewardId());
        if (!reward.orderable()) {
            throw new IllegalStateException("현재 후원할 수 없는 리워드입니다. rewardId=" + command.rewardId());
        }
        Cart cart = cartRepository.findByUserId(command.userId())
                .orElseGet(() -> Cart.create(command.userId()));
        cart.addItem(command.rewardId(), command.quantity());
        return CartView.from(cartRepository.save(cart));
    }

    @Transactional
    public CartView changeQuantity(Long userId, Long rewardId, int quantity) {
        Cart cart = getCartEntity(userId);
        cart.changeQuantity(rewardId, quantity);
        return CartView.from(cart);
    }

    @Transactional
    public CartView removeItem(Long userId, Long rewardId) {
        Cart cart = getCartEntity(userId);
        cart.removeItem(rewardId);
        return CartView.from(cart);
    }

    @Transactional
    public void clear(Long userId) {
        getCartEntity(userId).clear();
    }

    @Transactional(readOnly = true)
    public CartView getCart(Long userId) {
        return CartView.from(getCartEntity(userId));
    }

    private Cart getCartEntity(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("장바구니가 비어 있습니다. userId=" + userId));
    }
}
