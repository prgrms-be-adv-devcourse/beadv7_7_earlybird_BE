package com.growmighty.lectures.firstday.board.notice.presentation.dto;

import lombok.NonNull;

/** TODO(팀): user-service 연동 후 authorName은 본문에서 제거하고 조회한 값으로 대체 */
public record ProjectNoticeRequest(@NonNull String authorName, @NonNull String title, String content) {
}
