package com.growmighty.lectures.firstday.board.comment.presentation.dto;

import lombok.NonNull;

/** TODO(팀): 인증 도입 후 userId 는 토큰에서 추출하고 본문에서 제거 */
public record CommentRequest(@NonNull Long userId, Long parentId, @NonNull String content) {
}
