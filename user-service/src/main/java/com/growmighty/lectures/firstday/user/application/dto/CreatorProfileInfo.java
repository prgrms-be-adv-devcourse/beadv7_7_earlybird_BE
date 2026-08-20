package com.growmighty.lectures.firstday.user.application.dto;

import com.growmighty.lectures.firstday.user.domain.CreatorProfile;
import com.growmighty.lectures.firstday.user.domain.User;

public record CreatorProfileInfo(
        Long userId,
        String name,
        String phoneNumber,
        String bankName,
        String bankCode,
        String accountHolder
) {
    public static CreatorProfileInfo of(User user, CreatorProfile profile) {
        return new CreatorProfileInfo(user.getId(), user.getName(), user.getPhoneNumber(),
                profile.getBankName(), profile.getBankCode(), profile.getAccountHolder());
    }
}
