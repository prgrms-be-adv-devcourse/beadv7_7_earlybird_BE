package com.growmighty.lectures.firstday.seller.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sellers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    private Seller(Long userId, String businessName) {
        if (businessName == null || businessName.isBlank()) {
            throw new IllegalArgumentException("상호명은 필수입니다.");
        }
        this.userId = userId;
        this.businessName = businessName;
        this.status = SellerStatus.ACTIVE;
    }

    public static Seller apply(Long userId, String businessName) {
        return new Seller(userId, businessName);
    }

    public void suspend() {
        this.status = SellerStatus.SUSPENDED;
    }

    public void activate() {
        this.status = SellerStatus.ACTIVE;
    }

    public boolean canSell() {
        return this.status == SellerStatus.ACTIVE;
    }
}
