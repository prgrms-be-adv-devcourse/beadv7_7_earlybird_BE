package com.growmighty.lectures.firstday.user.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.user.domain.vo.AccountNumber;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 판매자(창작자) 등록 시 생성되는 정산 정보. userId 는 1인 1건(1:1, 논리 참조). */
@Entity
@Table(name = "creator_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorProfile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String bankName;

    /** 정산 계좌 — 민감정보. DB 컬럼에는 AES-GCM 암호문으로 저장된다 (#123). */
    @Column(nullable = false)
    private AccountNumber accountNumber;

    @Column(nullable = false)
    private String accountHolder;

    private CreatorProfile(Long userId, String bankName, String accountNumber, String accountHolder) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (bankName == null || bankName.isBlank() || accountNumber == null || accountNumber.isBlank()
                || accountHolder == null || accountHolder.isBlank()) {
            throw new IllegalArgumentException("정산 계좌 정보는 비어 있을 수 없습니다.");
        }
        this.userId = userId;
        this.bankName = bankName;
        this.accountNumber = new AccountNumber(accountNumber);
        this.accountHolder = accountHolder;
    }

    public static CreatorProfile register(Long userId, String bankName, String accountNumber, String accountHolder) {
        return new CreatorProfile(userId, bankName, accountNumber, accountHolder);
    }

    public void updateAccount(String bankName, String accountNumber, String accountHolder) {
        this.bankName = bankName;
        this.accountNumber = new AccountNumber(accountNumber);
        this.accountHolder = accountHolder;
    }
}
