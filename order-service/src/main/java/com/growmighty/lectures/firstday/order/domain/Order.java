package com.growmighty.lectures.firstday.order.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    private static final Money FREE_SHIPPING_THRESHOLD = Money.from(BigDecimal.valueOf(50_000));

    private static final Money BASE_SHIPPING_FEE = Money.from(BigDecimal.valueOf(3_000));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private final List<OrderItem> items = new ArrayList<>();

    @Column
    private Long paymentId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "items_amount", nullable = false))
    private Money itemsAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "shipping_fee", nullable = false))
    private Money shippingFee;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private Order(Long userId, List<OrderItem> items) {
        validateItems(items);
        items.forEach(this::addOrderItem);
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        recalculateAmounts();
    }

    public static Order create(Long userId, List<OrderItem> items) {
        return new Order(userId, items);
    }

    private void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("주문할 상품이 없습니다.");
        }
    }

    private void addOrderItem(OrderItem item) {
        this.items.add(item);

        if (item.getOrder() != this) {
            item.assignOrder(this);
        }
    }

    private void recalculateAmounts() {
        this.itemsAmount = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.zero(), Money::plus);
        this.shippingFee = calculateShippingFee(this.itemsAmount);
        this.totalAmount = this.itemsAmount.plus(this.shippingFee);
    }

    private Money calculateShippingFee(Money itemsAmount) {
        return itemsAmount.isGreaterThanOrEqual(FREE_SHIPPING_THRESHOLD)
                ? Money.zero()
                : BASE_SHIPPING_FEE;
    }

    public Money recalculatedTotal() {
        Money items = this.items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.zero(), Money::plus);
        return items.plus(calculateShippingFee(items));
    }

    public void completePayment(Long paymentId) {
        if (this.status != OrderStatus.CREATED) {
            throw new IllegalStateException("결제 가능한 상태가 아닙니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
    }

    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void changeItemPrice(Long orderItemId, BigDecimal newPrice) {
        findItem(orderItemId).changePrice(newPrice);
        recalculateAmounts();
    }

    public void changeItemQuantity(Long orderItemId, int newQuantity) {
        findItem(orderItemId).changeQuantity(newQuantity);
        recalculateAmounts();
    }

    private OrderItem findItem(Long orderItemId) {
        return items.stream()
                .filter(e -> e.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("주문에 없는 항목입니다."));
    }
}
