package com.growmighty.lectures.firstday.project.reward.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order-service가 보낸 (orderId, rewardId, operation) 조합을 기록해 재고 변경 요청의 중복 도착을
 * 판별한다(#195). 유니크 제약(order_id, reward_id, operation) 위반이 곧 "이미 처리된 요청"이라는
 * 신호다 — 같은 (orderId, rewardId)라도 DECREASE와 RESTORE는 서로 다른 정상 이벤트(주문 시 차감 →
 * 이후 취소 시 복원)라 operation까지 키에 포함한다.
 */
@Entity
@Table(name = "stock_change_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "reward_id", "operation"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockChangeLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reward_id", nullable = false)
    private Long rewardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockChangeOperation operation;

    private StockChangeLog(Long orderId, Long rewardId, StockChangeOperation operation) {
        this.orderId = orderId;
        this.rewardId = rewardId;
        this.operation = operation;
    }

    public static StockChangeLog of(Long orderId, Long rewardId, StockChangeOperation operation) {
        return new StockChangeLog(orderId, rewardId, operation);
    }
}
