package com.growmighty.lectures.firstday.user.presentation.dto;

import lombok.NonNull;

public record RefreshRequest(@NonNull String refreshToken) {
}
