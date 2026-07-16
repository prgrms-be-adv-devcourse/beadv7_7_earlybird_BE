package com.growmighty.lectures.firstday.project.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 리워드 = 펀딩 액수별 후원 옵션(상품). 한정 수량 리워드(얼리버드) 포함.
 * 프로젝트와는 ID로만 참조한다 (같은 컨텍스트, 별도 애그리거트).
 */
@Entity
@Table(name = "rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reward extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    /** 총 수량. TODO(팀): 무제한 리워드(수량 제한 없음) 허용 여부 결정 — 현재는 필수값 */
    @Column(nullable = false)
    private Integer totalQuantity;

    @Column(nullable = false)
    private Integer remainingQuantity;

    private Reward(Long projectId, String name, String description, BigDecimal price, Integer totalQuantity) {
        validatePrice(price);
        if (totalQuantity == null || totalQuantity < 0) {
            throw new IllegalArgumentException("수량은 0개 이상이어야 합니다. 입력값: " + totalQuantity);
        }
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
    }

    public static Reward register(Long projectId, String name, String description,
                                  BigDecimal price, Integer totalQuantity) {
        return new Reward(projectId, name, description, price, totalQuantity);
    }

    // TODO(팀): 한정 수량 리워드의 동시성 제어 정책 결정 (@Version 낙관적 락 vs 비관적 락 vs 원자적 UPDATE).
    //           선착순 얼리버드가 학습 포인트 — 부하 테스트로 검증할 것.
    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 1개 이상이어야 합니다.");
        }
        if (this.remainingQuantity < quantity) {
            throw new IllegalStateException(
                "재고가 부족합니다. reward=" + this.name + ", 재고=" + this.remainingQuantity + ", 요청=" + quantity);
        }
        this.remainingQuantity -= quantity;
    }

    /** 후원 취소·일괄 환불 시 재고 복원 */
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복원 수량은 1개 이상이어야 합니다.");
        }
        if (this.remainingQuantity + quantity > this.totalQuantity) {
            throw new IllegalStateException(
                "복원 후 재고가 총 수량을 초과할 수 없습니다. reward=" + this.name
                    + ", 재고=" + this.remainingQuantity + ", 복원=" + quantity + ", 총수량=" + this.totalQuantity);
        }
        this.remainingQuantity += quantity;
    }

    /**
     * 판매 개시 후 수량은 "추가만 허용, 축소 불가" (기획 정책 — 락 경합·판매분 초과 방지).
     * TODO(팀): 판매 개시 여부 판단 기준과 부득이한 축소(판매 수량 하한) 정책 확정
     */
    public void increaseQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("추가 수량은 1개 이상이어야 합니다.");
        }
        this.totalQuantity += amount;
        this.remainingQuantity += amount;
    }

    /** TODO(팀): 부모 프로젝트가 OPEN인지 함께 검증하는 위치(애플리케이션 서비스) 결정 */
    public boolean isOrderable() {
        return this.remainingQuantity > 0;
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0원보다 커야 합니다. 입력값: " + price);
        }
    }
}
