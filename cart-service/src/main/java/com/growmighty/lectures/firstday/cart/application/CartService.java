package com.growmighty.lectures.firstday.cart.application;

import com.growmighty.lectures.firstday.cart.application.dto.AddCartItemCommand;
import com.growmighty.lectures.firstday.cart.application.dto.CartView;
import com.growmighty.lectures.firstday.cart.domain.Cart;
import com.growmighty.lectures.firstday.cart.domain.CartRepository;
import com.growmighty.lectures.firstday.cart.application.port.ProjectPort;
import com.growmighty.lectures.firstday.cart.application.port.dto.ProjectSnapshot;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartRepository cartRepository;
    private final ProjectPort projectPort;

    // TODO(팀): 장바구니(후원 바구니) 항목을 rewardId 기반으로 재설계 — 후원은 리워드 단위다.
    //           도메인 다이어그램에 Cart 가 아직 없지만 명세서(장바구니) 필수 요구사항이므로 팀 확정 필요.
    @Transactional
    public CartView addItem(AddCartItemCommand command) {
        ProjectSnapshot project = projectPort.getProject(command.projectId());
        if (!project.orderable()) {
            throw new IllegalStateException("현재 후원할 수 없는 프로젝트입니다. projectId=" + command.projectId());
        }
        Cart cart = cartRepository.findByUserId(command.userId())
                .orElseGet(() -> Cart.create(command.userId()));
        cart.addItem(command.projectId(), command.quantity());
        return CartView.from(cartRepository.save(cart));
    }

    @Transactional
    public CartView changeQuantity(Long userId, Long projectId, int quantity) {
        Cart cart = getCartEntity(userId);
        cart.changeQuantity(projectId, quantity);
        return CartView.from(cart);
    }

    @Transactional
    public CartView removeItem(Long userId, Long projectId) {
        Cart cart = getCartEntity(userId);
        cart.removeItem(projectId);
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
