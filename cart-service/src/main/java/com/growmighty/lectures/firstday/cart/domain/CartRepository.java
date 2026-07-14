package com.growmighty.lectures.firstday.cart.domain;

import java.util.Optional;

public interface CartRepository {
    Cart save(Cart cart);

    Optional<Cart> findByUserId(Long userId);
}
