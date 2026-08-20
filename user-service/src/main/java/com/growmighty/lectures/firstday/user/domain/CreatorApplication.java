package com.growmighty.lectures.firstday.user.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 후원자(BACKER)의 창작자 전환 신청. 관리자 승인/반려를 거쳐야 하며, 승인 시에만
 *  {@link User#becomeCreator()} + {@link CreatorProfile} 생성으로 이어진다(#448). */
@Entity
@Table(name = "creator_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatorApplication extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String creatorName;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 1000)
    private String introduction;

    private String businessNumber;

    private String portfolioUrl;

    @Column(nullable = false)
    private String bankName;

    /** 토스페이먼츠 지급대행 기관 코드(두 자리) — https://docs.tosspayments.com/codes/org-codes (CreatorProfile과 동일) */
    @Column(nullable = false)
    private String bankCode;

    /** 정산 계좌 — 민감정보. TODO(팀): AES 암호화 방식·키 관리 확정 후 저장 전 암호화 적용 (CreatorProfile과 동일 이슈) */
    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreatorApplicationStatus status;

    private String rejectReason;

    private CreatorApplication(Long userId, String creatorName, String category, String introduction,
                                String businessNumber, String portfolioUrl,
                                String bankCode, String accountNumber, String accountHolder) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (creatorName == null || creatorName.isBlank() || category == null || category.isBlank()
                || introduction == null || introduction.isBlank()) {
            throw new IllegalArgumentException("창작자명, 카테고리, 소개글은 비어 있을 수 없습니다.");
        }
        if (accountNumber == null || accountNumber.isBlank() || accountHolder == null || accountHolder.isBlank()) {
            throw new IllegalArgumentException("정산 계좌 정보는 비어 있을 수 없습니다.");
        }
        BankCode bank = BankCode.fromCode(bankCode);
        this.userId = userId;
        this.creatorName = creatorName;
        this.category = category;
        this.introduction = introduction;
        this.businessNumber = businessNumber;
        this.portfolioUrl = portfolioUrl;
        this.bankName = bank.getBankName();
        this.bankCode = bank.getCode();
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.status = CreatorApplicationStatus.PENDING;
    }

    public static CreatorApplication apply(Long userId, String creatorName, String category, String introduction,
                                            String businessNumber, String portfolioUrl,
                                            String bankCode, String accountNumber, String accountHolder) {
        return new CreatorApplication(userId, creatorName, category, introduction,
                businessNumber, portfolioUrl, bankCode, accountNumber, accountHolder);
    }

    public void approve() {
        requireStatus(CreatorApplicationStatus.PENDING, "승인은 심사 대기 상태에서만 가능합니다.");
        this.status = CreatorApplicationStatus.APPROVED;
    }

    public void reject(String reason) {
        requireStatus(CreatorApplicationStatus.PENDING, "반려는 심사 대기 상태에서만 가능합니다.");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 비어 있을 수 없습니다.");
        }
        this.status = CreatorApplicationStatus.REJECTED;
        this.rejectReason = reason;
    }

    private void requireStatus(CreatorApplicationStatus expected, String message) {
        if (this.status != expected) {
            throw new IllegalStateException(message + " 현재 상태=" + this.status);
        }
    }
}
