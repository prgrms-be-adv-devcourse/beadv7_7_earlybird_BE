package com.growmighty.lectures.firstday.cart.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {
    public static final int MAX_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    private CartItem(Long productId, int quantity) {
        validateQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    public static CartItem create(Long productId, int quantity) {
        return new CartItem(productId, quantity);
    }

    void addQuantity(int amount) {
        validateQuantity(this.quantity + amount);
        this.quantity += amount;
    }

    void changeQuantity(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    boolean hasProduct(Long productId) {
        return this.productId.equals(productId);
    }

    void assignCart(Cart cart) {
        this.cart = cart;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다. 입력값: " + quantity);
        }
        if (quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("한 상품은 최대 " + MAX_QUANTITY + "개까지 담을 수 있습니다.");
        }
    }
}
