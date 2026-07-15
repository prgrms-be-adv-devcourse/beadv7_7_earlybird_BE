package com.growmighty.lectures.firstday.notification.presentation;

import com.growmighty.lectures.firstday.notification.application.NotificationService;
import com.growmighty.lectures.firstday.notification.presentation.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 알림 API. TODO(팀): 인증 도입 후 userId 쿼리 파라미터 대신 토큰에서 추출 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/me")
    public List<NotificationResponse> getMyNotifications(@RequestParam Long userId) {
        return notificationService.getByUser(userId).stream()
            .map(NotificationResponse::from).toList();
    }

    @PatchMapping("/{notificationId}/read")
    public Void markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return null;
    }

    @PatchMapping("/read-all")
    public Void markAllAsRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return null;
    }
}
