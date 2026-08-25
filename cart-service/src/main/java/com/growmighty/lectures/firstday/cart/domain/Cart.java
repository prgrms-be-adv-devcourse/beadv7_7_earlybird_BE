package com.growmighty.lectures.firstday.cart.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "carts", uniqueConstraints = @UniqueConstraint(
        name = "uk_carts_user_id",
        columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart extends BaseEntity {
    public static final int MAX_DISTINCT_ITEMS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<CartItem> items = new ArrayList<>();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Cart(Long userId) {
        this.userId = userId;
    }

    public static Cart create(Long userId) {
        return new Cart(userId);
    }

    public void addItem(Long rewardId, int quantity) {
        CartItem existing = findItem(rewardId);
        if (existing != null) {
            existing.addQuantity(quantity);
            return;
        }
        if (items.size() >= MAX_DISTINCT_ITEMS) {
            throw new IllegalStateException("장바구니에는 최대 " + MAX_DISTINCT_ITEMS + "종류까지 담을 수 있습니다.");
        }
        CartItem item = CartItem.create(rewardId, quantity);
        item.assignCart(this);
        this.items.add(item);
    }

    public void changeQuantity(Long rewardId, int newQuantity) {
        requireItem(rewardId).changeQuantity(newQuantity);
    }

    public void setItemQuantity(Long rewardId, int quantity) {
        CartItem existing = findItem(rewardId);
        if (existing != null) {
            existing.changeQuantity(quantity);
            return;
        }
        addItem(rewardId, quantity);
    }

    public int quantityOf(Long rewardId) {
        CartItem item = findItem(rewardId);
        return item == null ? 0 : item.getQuantity();
    }

    public boolean containsReward(Long rewardId) {
        return findItem(rewardId) != null;
    }

    public void removeItem(Long rewardId) {
        requireItem(rewardId);
        this.items.removeIf(item -> item.hasReward(rewardId));
    }

    public void removeItems(Collection<Long> rewardIds) {
        this.items.removeIf(item -> rewardIds.contains(item.getRewardId()));
    }

    public void clear() {
        this.items.clear();
    }

    private CartItem findItem(Long rewardId) {
        return items.stream()
                .filter(item -> item.hasReward(rewardId))
                .findFirst()
                .orElse(null);
    }

    private CartItem requireItem(Long rewardId) {
        CartItem item = findItem(rewardId);
        if (item == null) {
            throw new IllegalArgumentException("장바구니에 없는 리워드입니다. rewardId=" + rewardId);
        }
        return item;
    }
}
