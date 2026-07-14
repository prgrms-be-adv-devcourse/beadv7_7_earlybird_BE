package com.growmighty.lectures.firstday.settlement.read;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 정산 서비스가 소유한 주문 <b>read-model</b>.
 *
 * <p>서비스가 분리되면서 정산 DB 는 주문 DB 와 물리적으로 분리됐다. 정산 배치는 order-service 의
 * {@code Order} 엔티티를 컴파일 타임에 알지 않고, 자기 DB 의 {@code orders} 테이블을 이 read-model 로
 * 읽는다. (실습에서는 {@code SettlementDataSeeder} 가 이 테이블에 대량 데이터를 직접 적재한다 —
 * 실제 MSA 라면 이벤트/CDC 로 복제하거나 주문 서비스의 조회 API 를 페이징 호출했을 자리다.)
 *
 * <p>주문의 쓰기 모델(items 등)은 정산에 필요 없으므로 담지 않는다 — 필요한 만큼만 읽는다.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

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
}
