package com.growmighty.lectures.firstday.cart.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {
    public static final int MAX_DISTINCT_ITEMS = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CartItem> items = new ArrayList<>();

    @Column(nullable = false)
    private Long userId;

    private Cart(Long userId) {
        this.userId = userId;
    }

    public static Cart create(Long userId) {
        return new Cart(userId);
    }

    public void addItem(Long productId, int quantity) {
        CartItem existing = findItem(productId);
        if (existing != null) {
            existing.addQuantity(quantity);
            return;
        }
        if (items.size() >= MAX_DISTINCT_ITEMS) {
            throw new IllegalStateException("장바구니에는 최대 " + MAX_DISTINCT_ITEMS + "종류까지 담을 수 있습니다.");
        }
        CartItem item = CartItem.create(productId, quantity);
        item.assignCart(this);
        this.items.add(item);
    }

    public void changeQuantity(Long productId, int newQuantity) {
        requireItem(productId).changeQuantity(newQuantity);
    }

    public void removeItem(Long productId) {
        requireItem(productId);
        this.items.removeIf(item -> item.hasProduct(productId));
    }

    public void clear() {
        this.items.clear();
    }

    private CartItem findItem(Long productId) {
        return items.stream()
                .filter(item -> item.hasProduct(productId))
                .findFirst()
                .orElse(null);
    }

    private CartItem requireItem(Long productId) {
        CartItem item = findItem(productId);
        if (item == null) {
            throw new IllegalArgumentException("장바구니에 없는 상품입니다. productId=" + productId);
        }
        return item;
    }
}
