package com.growmighty.lectures.firstday.seller.presentation.dto;

import com.growmighty.lectures.firstday.seller.application.dto.ApplySellerCommand;
import lombok.NonNull;

public record ApplySellerRequest(@NonNull Long userId, @NonNull String businessName) {
    public ApplySellerCommand toCommand() {
        return new ApplySellerCommand(userId, businessName);
    }
}
