package com.growmighty.lectures.firstday.order.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_user_id_idempotency_key",
        columnNames = {"user_id", "order_idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {
    private static final Money FREE_SHIPPING_THRESHOLD = Money.from(BigDecimal.valueOf(50_000));
    private static final Money BASE_SHIPPING_FEE = Money.from(BigDecimal.valueOf(3_000));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_idempotency_key", nullable = false, updatable = false)
    private UUID orderIdempotencyKey;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private final List<OrderItem> items = new ArrayList<>();

    @Column(name = "project_id", nullable = false)
    private Long projectId;

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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private String receiverName;

    @Column(nullable = false)
    private String receiverPhone;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private String zipCode;

    private Order(Long id, Long userId, Long projectId, List<OrderItem> items,
                  String receiverName, String receiverPhone, String shippingAddress, String zipCode,
                  UUID orderIdempotencyKey) {
        validateItems(items);
        validateShippingInfo(receiverName, receiverPhone, shippingAddress, zipCode);
        validateOrderIdempotencyKey(orderIdempotencyKey);
        this.id = id;
        items.forEach(this::addOrderItem);
        this.userId = userId;
        this.projectId = projectId;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.shippingAddress = shippingAddress;
        this.zipCode = zipCode;
        this.orderIdempotencyKey = orderIdempotencyKey;
        this.status = OrderStatus.CREATED;
        recalculateAmounts();
    }

    public static Order create(Long id, Long userId, Long projectId, List<OrderItem> items,
                               String receiverName, String receiverPhone, String shippingAddress, String zipCode,
                               UUID orderIdempotencyKey) {
        return new Order(id, userId, projectId, items, receiverName, receiverPhone, shippingAddress, zipCode,
                orderIdempotencyKey);
    }

    private void validateOrderIdempotencyKey(UUID orderIdempotencyKey) {
        if (orderIdempotencyKey == null) {
            throw new IllegalArgumentException("orderIdempotencyKey is required.");
        }
    }

    private void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Order must contain at least one item.");
        }
    }

    private void validateShippingInfo(String receiverName, String receiverPhone, String shippingAddress, String zipCode) {
        if (receiverName == null || receiverName.isBlank()
                || receiverPhone == null || receiverPhone.isBlank()
                || shippingAddress == null || shippingAddress.isBlank()
                || zipCode == null || zipCode.isBlank()) {
            throw new IllegalArgumentException("Shipping information is required.");
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

    public void markStockReservationFailed() {
        changeStatus(OrderStatus.CREATED, OrderStatus.STOCK_FAILED);
    }

    public void markPaymentRequested() {
        changeStatus(OrderStatus.CREATED, OrderStatus.PAYMENT_REQUEST);
    }

    public void markPaymentProcessing() {
        changeStatus(OrderStatus.PAYMENT_REQUEST, OrderStatus.PAYMENT_PROCESSING);
    }

    public void markPaymentFailed() {
        if (this.status != OrderStatus.PAYMENT_REQUEST && this.status != OrderStatus.PAYMENT_PROCESSING) {
            throw new InvalidOrderStatusException(this.status, OrderStatus.PAYMENT_FAILED);
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void markPaid() {
        if (this.status != OrderStatus.PAYMENT_REQUEST && this.status != OrderStatus.PAYMENT_PROCESSING) {
            throw new InvalidOrderStatusException(this.status, OrderStatus.PAID);
        }
        this.status = OrderStatus.PAID;
    }

    public void cancel() {
        changeStatus(OrderStatus.PAID, OrderStatus.CANCELLED);
    }

    public boolean isCancelled() {
        return this.status == OrderStatus.CANCELLED;
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
                .orElseThrow(() -> new IllegalArgumentException("Order item not found."));
    }

    private void changeStatus(OrderStatus expectedStatus, OrderStatus nextStatus) {
        if (this.status != expectedStatus) {
            throw new InvalidOrderStatusException(this.status, nextStatus);
        }
        this.status = nextStatus;
    }
}
