package com.growmighty.lectures.firstday.settlement.read;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 정산 read-model 의 결제 테이블 매핑.
 *
 * <p>배치가 직접 읽지는 않지만, {@code SettlementDataSeeder} 가 orders 와 1:1 로 payments 도 적재하므로
 * (orders ⨝ payments 조인 실습용) {@code payments} 테이블이 존재해야 한다. ddl-auto 가 이 엔티티로
 * 테이블을 만들어 준다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status;
}
