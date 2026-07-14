package com.growmighty.lectures.firstday.project.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Lob
    private String description;

    @Column(nullable = false)
    private Long salesCount;

    private Project(Long sellerId, String name, BigDecimal price, Integer stockQuantity, String description) {
        validatePrice(price);
        if (stockQuantity == null || stockQuantity < 0) {
            throw new IllegalArgumentException("재고는 0개 이상이어야 합니다. 입력값: " + stockQuantity);
        }
        this.sellerId = sellerId;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.description = description;
        this.status = stockQuantity == 0 ? ProjectStatus.OUT_OF_STOCK : ProjectStatus.ON_SALE;
        this.salesCount = 0L;
    }

    public static Project register(Long sellerId, String name, BigDecimal price, Integer stockQuantity, String description) {
        return new Project(sellerId, name, price, stockQuantity, description);
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("차감 수량은 1개 이상이어야 합니다.");
        }
        if (this.status == ProjectStatus.DISCONTINUED) {
            throw new IllegalStateException("판매 종료된 프로젝트입니다. project=" + this.name);
        }
        if (this.stockQuantity < quantity) {
            throw new IllegalStateException(
                "재고가 부족합니다. project=" + this.name + ", 재고=" + this.stockQuantity + ", 요청=" + quantity);
        }
        this.stockQuantity -= quantity;
        this.salesCount += quantity;
        if (this.stockQuantity == 0) {
            this.status = ProjectStatus.OUT_OF_STOCK;
        }
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복원 수량은 1개 이상이어야 합니다.");
        }
        this.stockQuantity += quantity;
        this.salesCount = Math.max(0, this.salesCount - quantity);
        if (this.status == ProjectStatus.OUT_OF_STOCK) {
            this.status = ProjectStatus.ON_SALE;
        }
    }

    public void changePrice(BigDecimal newPrice) {
        validatePrice(newPrice);
        this.price = newPrice;
    }

    public void discontinue() {
        this.status = ProjectStatus.DISCONTINUED;
    }

    public boolean isOrderable() {
        return this.status == ProjectStatus.ON_SALE;
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가격은 0원보다 커야 합니다. 입력값: " + price);
        }
    }
}
