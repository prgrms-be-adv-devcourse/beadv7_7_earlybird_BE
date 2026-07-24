package com.growmighty.lectures.firstday.cart.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 장바구니 항목 — 후원은 리워드(후원 옵션) 단위로 이루어진다. 프로젝트 정보는 reward → project 경유 조회. */
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_items_cart_id_reward_id",
                columnNames = {"cart_id", "reward_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {
    public static final int MAX_QUANTITY = 99;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @Column(nullable = false)
    private Long rewardId;

    @Column(nullable = false)
    private Integer quantity;

    private CartItem(Long rewardId, int quantity) {
        validateQuantity(quantity);
        this.rewardId = rewardId;
        this.quantity = quantity;
    }

    public static CartItem create(Long rewardId, int quantity) {
        return new CartItem(rewardId, quantity);
    }

    void addQuantity(int amount) {
        validateQuantity(this.quantity + amount);
        this.quantity += amount;
    }

    void changeQuantity(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    boolean hasReward(Long rewardId) {
        return this.rewardId.equals(rewardId);
    }

    void assignCart(Cart cart) {
        this.cart = cart;
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다. 입력값: " + quantity);
        }
        if (quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("한 리워드는 최대 " + MAX_QUANTITY + "개까지 담을 수 있습니다.");
        }
    }
}
