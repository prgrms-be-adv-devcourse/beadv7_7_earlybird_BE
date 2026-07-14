package com.growmighty.lectures.firstday.cart.application;

import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemCommand;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import com.growmighty.lectures.firstday.cart.domain.Cart;
import com.growmighty.lectures.firstday.cart.domain.CartRepository;
import com.growmighty.lectures.firstday.cart.application.port.ProductPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.ProductSnapshot;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProductPort productPort;

    @Transactional
    public CartView addItem(AddCartItemCommand command) {
        ProductSnapshot product = productPort.getProduct(command.productId());
        if (!product.orderable()) {
            throw new IllegalStateException("현재 구매할 수 없는 상품입니다. productId=" + command.productId());
        }
        Cart cart = cartRepository.findByUserId(command.userId())
                .orElseGet(() -> Cart.create(command.userId()));
        cart.addItem(command.productId(), command.quantity());
        return CartView.from(cartRepository.save(cart));
    }

    @Transactional
    public CartView changeQuantity(Long userId, Long productId, int quantity) {
        Cart cart = getCartEntity(userId);
        cart.changeQuantity(productId, quantity);
        return CartView.from(cart);
    }

    @Transactional
    public CartView removeItem(Long userId, Long productId) {
        Cart cart = getCartEntity(userId);
        cart.removeItem(productId);
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
