package com.growmighty.lectures.firstday.seller.presentation.dto;

import com.growmighty.lectures.firstday.seller.application.dto.SellerInfo;

public record SellerResponse(
        Long id,
        Long userId,
        String businessName,
        String status
) {
    public static SellerResponse from(SellerInfo info) {
        return new SellerResponse(info.id(), info.userId(), info.businessName(), info.status().name());
    }
}
