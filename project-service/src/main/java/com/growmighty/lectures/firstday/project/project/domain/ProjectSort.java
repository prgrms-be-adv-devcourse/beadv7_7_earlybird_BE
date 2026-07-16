package com.growmighty.lectures.firstday.project.project.domain;

import org.springframework.data.domain.Sort;

public enum ProjectSort {
    LATEST,          // 최신 등록순
    DEADLINE,        // 마감 임박순
    FUNDED_AMOUNT;   // 펀딩액 많은순

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case DEADLINE -> Sort.by(Sort.Direction.ASC, "endAt");
            case FUNDED_AMOUNT -> Sort.by(Sort.Direction.DESC, "fundedAmount");
        };
    }
}
