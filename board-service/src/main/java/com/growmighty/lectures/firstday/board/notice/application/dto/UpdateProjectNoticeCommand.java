package com.growmighty.lectures.firstday.board.notice.application.dto;

import com.growmighty.lectures.firstday.common.entity.UserRole;

public record UpdateProjectNoticeCommand(Long noticeId, Long requesterId, UserRole requesterRole, String title, String content) {
}