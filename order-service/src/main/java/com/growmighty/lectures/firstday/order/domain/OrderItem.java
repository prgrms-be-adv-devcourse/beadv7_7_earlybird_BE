package com.growmighty.lectures.firstday.order.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "price", nullable = false))
    private Money price;

    @Column(nullable = false)
    private Long projectId;

    /** 후원은 리워드(후원 옵션) 단위 — 재고 차감/복원의 기준. TODO(팀): 순수 후원(리워드 없음) 허용 시 nullable 로 변경 */
    @Column(nullable = false)
    private Long rewardId;

    @Column(nullable = false)
    private Integer quantity;

    public static OrderItem create(String name, BigDecimal price, Long projectId, Long rewardId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }
        OrderItem orderItem = new OrderItem();
        orderItem.name = name;
        orderItem.price = Money.from(price);
        orderItem.projectId = projectId;
        orderItem.rewardId = rewardId;
        orderItem.quantity = quantity;

        return orderItem;
    }

    public Money subtotal() {
        return price.times(quantity);
    }

    public void changePrice(BigDecimal newPrice) {
        this.price = Money.from(newPrice);
    }

    public void changeQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }
        this.quantity = newQuantity;
    }

    void assignOrder(Order order) {
        this.order = order;
    }
}
