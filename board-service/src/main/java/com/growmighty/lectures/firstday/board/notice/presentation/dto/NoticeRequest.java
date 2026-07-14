package com.growmighty.lectures.firstday.board.notice.presentation.dto;

import lombok.NonNull;

public record NoticeRequest(@NonNull String title, String content) {
}
