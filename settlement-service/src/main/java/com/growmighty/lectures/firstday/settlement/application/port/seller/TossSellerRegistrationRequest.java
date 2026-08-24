package com.growmighty.lectures.firstday.settlement.application.port.seller;

import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import java.util.Objects;

public record TossSellerRegistrationRequest(
        Long creatorId,
        CreatorInformation creatorInformation
) {

    public TossSellerRegistrationRequest {
        if (creatorId == null || creatorId <= 0) {
            throw new IllegalArgumentException("창작자 식별자는 양수여야 합니다.");
        }
        creatorInformation = Objects.requireNonNull(creatorInformation, "창작자 정보는 필수입니다.");
    }
}
