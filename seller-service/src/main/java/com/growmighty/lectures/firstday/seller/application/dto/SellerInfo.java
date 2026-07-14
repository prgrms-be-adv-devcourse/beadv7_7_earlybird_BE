package com.growmighty.lectures.firstday.seller.application.dto;

import com.growmighty.lectures.firstday.seller.domain.Seller;
import com.growmighty.lectures.firstday.seller.domain.SellerStatus;

public record SellerInfo(
        Long id,
        Long userId,
        String businessName,
        SellerStatus status
) {
    public static SellerInfo from(Seller seller) {
        return new SellerInfo(seller.getId(), seller.getUserId(), seller.getBusinessName(), seller.getStatus());
    }
}
