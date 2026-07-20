package com.growmighty.lectures.firstday.project.reward.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 리워드 = 펀딩 액수별 후원 옵션(상품). 한정 수량 리워드(얼리버드) 포함.
 * 프로젝트와는 ID로만 참조한다 (같은 컨텍스트, 별도 애그리거트).
 * totalQuantity가 null이면 무제한 리워드 — 재고 검증/차감·복원을 건너뛴다.
 */
@Entity
@Table(name = "rewards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reward extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rewardId;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    /** null이면 무제한 리워드 */
    private Integer totalQuantity;

    private Integer remainingQuantity;

    /** 동시 차감/복원 요청 사이의 낙관적 락 — 재고 갱신 충돌 감지용 */
    @Version
    private Long version;

    /** 공개 후 삭제 요청 시 하드 삭제 대신 이 값만 false로 바꾼다 (이미 후원 데이터가 참조하고 있을 수 있어 레코드는 보존) */
    @Column(nullable = false)
    private boolean active;

    private Reward(Long projectId, String name, String description, BigDecimal price, Integer totalQuantity) {
        validatePrice(price);
        if (totalQuantity != null && totalQuantity < 0) {
            throw new IllegalArgumentException("수량은 0개 이상이어야 합니다. 입력값: " + totalQuantity);
        }
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.active = true;
    }

    public static Reward register(Long projectId, String name, String description,
                                  BigDecimal price, Integer totalQuantity) {
        return new Reward(projectId, name, description, price, totalQuantity);
    }

    public void decreaseStock(int quantity) {
        validateQuantity(quantity, "차감");
        if (!this.active) {
            throw new IllegalStateException("판매 종료된 리워드는 주문할 수 없습니다. reward=" + this.name);
        }
        if (this.totalQuantity == null) {
            return;
        }
        if (this.remainingQuantity < quantity) {
            throw new IllegalStateException(
                "재고가 부족합니다. reward=" + this.name + ", 재고=" + this.remainingQuantity + ", 요청=" + quantity);
        }
        this.remainingQuantity -= quantity;
    }

    /** 후원 취소·일괄 환불 시 재고 복원 */
    public void restoreStock(int quantity) {
        validateQuantity(quantity, "복원");
        if (this.totalQuantity == null) {
            return;
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
        if (this.totalQuantity == null) {
            throw new IllegalStateException("무제한 리워드는 수량을 추가할 대상이 없습니다.");
        }
        this.totalQuantity += amount;
        this.remainingQuantity += amount;
    }

    /** 공개 전: 자유 수정 (null 필드는 미변경). 공개 전이라 아직 판매된 적 없으므로 totalQuantity를 바꾸면 remainingQuantity도 그대로 맞춘다. */
    public void updateBeforePublish(String name, String description, BigDecimal price, Integer totalQuantity) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            validatePrice(price);
            this.price = price;
        }
        if (totalQuantity != null) {
            if (totalQuantity < 0) {
                throw new IllegalArgumentException("수량은 0개 이상이어야 합니다. 입력값: " + totalQuantity);
            }
            this.totalQuantity = totalQuantity;
            this.remainingQuantity = totalQuantity;
        }
    }

    /** 공개 후 삭제 요청을 받았을 때 호출 — 하드 삭제 대신 판매 종료 처리만 한다 */
    public void deactivate() {
        this.active = false;
    }

    /** TODO(팀): 부모 프로젝트가 OPEN인지 함께 검증하는 위치(애플리케이션 서비스) 결정 */
    public boolean isOrderable() {
        return this.active && (this.totalQuantity == null || this.remainingQuantity > 0);
    }

    private void validateQuantity(int quantity, String action) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(action + " 수량은 1개 이상이어야 합니다.");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0원보다 커야 합니다. 입력값: " + price);
        }
    }
}
