package com.growmighty.lectures.firstday.board.comment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** register/registerReply/update가 공유 — 셋 다 필요로 하는 필드가 content 하나뿐이라 나눌 이유가 없다. */
public record CommentRequest(@NotBlank String content) {
}