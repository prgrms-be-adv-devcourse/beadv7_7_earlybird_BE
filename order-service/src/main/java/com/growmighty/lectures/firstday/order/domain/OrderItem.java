package com.growmighty.lectures.firstday.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
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
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    public static OrderItem create(String name, BigDecimal price, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }
        OrderItem orderItem = new OrderItem();
        orderItem.name = name;
        orderItem.price = Money.from(price);
        orderItem.productId = productId;
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
