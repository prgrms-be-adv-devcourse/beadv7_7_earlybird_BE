package com.growmighty.lectures.firstday.settlement.application.port.seller;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import java.util.Objects;

public sealed interface TossSellerRegistrationResult
        permits TossSellerRegistrationResult.Registered, TossSellerRegistrationResult.Rejected {

    record Registered(String sellerId, CreatorPayoutStatus payoutStatus) implements TossSellerRegistrationResult {
        public Registered {
            if (sellerId == null || sellerId.isBlank()) {
                throw new IllegalArgumentException("토스 셀러 식별자는 필수입니다.");
            }
            payoutStatus = Objects.requireNonNull(payoutStatus, "창작자 지급 상태는 필수입니다.");
            if (payoutStatus == CreatorPayoutStatus.REGISTRATION_PENDING) {
                throw new IllegalArgumentException("등록 대기 상태는 셀러 등록 결과가 될 수 없습니다.");
            }
        }
    }

    record Rejected(String errorCode) implements TossSellerRegistrationResult {
        public Rejected {
            if (errorCode == null || errorCode.isBlank()) {
                throw new IllegalArgumentException("셀러 등록 거절 코드는 필수입니다.");
            }
        }
    }
}
