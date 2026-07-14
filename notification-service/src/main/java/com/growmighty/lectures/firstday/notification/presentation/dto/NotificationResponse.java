package com.growmighty.lectures.firstday.notification.presentation.dto;

import com.growmighty.lectures.firstday.notification.domain.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, Long userId, String type, String message, boolean isRead, LocalDateTime createdAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getType(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
