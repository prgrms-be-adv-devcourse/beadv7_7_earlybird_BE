package com.growmighty.lectures.firstday.board.notice.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectNoticeRequest(@NotBlank String title, @NotBlank String content) {
}
