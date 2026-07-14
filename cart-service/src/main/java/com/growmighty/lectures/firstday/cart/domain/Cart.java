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

    public void addItem(Long projectId, int quantity) {
        CartItem existing = findItem(projectId);
        if (existing != null) {
            existing.addQuantity(quantity);
            return;
        }
        if (items.size() >= MAX_DISTINCT_ITEMS) {
            throw new IllegalStateException("장바구니에는 최대 " + MAX_DISTINCT_ITEMS + "종류까지 담을 수 있습니다.");
        }
        CartItem item = CartItem.create(projectId, quantity);
        item.assignCart(this);
        this.items.add(item);
    }

    public void changeQuantity(Long projectId, int newQuantity) {
        requireItem(projectId).changeQuantity(newQuantity);
    }

    public void removeItem(Long projectId) {
        requireItem(projectId);
        this.items.removeIf(item -> item.hasProject(projectId));
    }

    public void clear() {
        this.items.clear();
    }

    private CartItem findItem(Long projectId) {
        return items.stream()
                .filter(item -> item.hasProject(projectId))
                .findFirst()
                .orElse(null);
    }

    private CartItem requireItem(Long projectId) {
        CartItem item = findItem(projectId);
        if (item == null) {
            throw new IllegalArgumentException("장바구니에 없는 프로젝트입니다. projectId=" + projectId);
        }
        return item;
    }
}
