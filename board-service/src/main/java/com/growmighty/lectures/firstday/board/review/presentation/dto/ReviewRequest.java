package com.growmighty.lectures.firstday.board.review.presentation.dto;

import lombok.NonNull;

/** TODO(팀): 인증 도입 후 userId 는 토큰에서 추출하고 본문에서 제거 */
public record ReviewRequest(@NonNull Long userId, @NonNull Integer rating, String content) {
}
