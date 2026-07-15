package com.growmighty.lectures.firstday.notification.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
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
    public ApiResponse<List<NotificationResponse>> getMyNotifications(@RequestParam Long userId) {
        return ApiResponse.ok(notificationService.getByUser(userId).stream()
            .map(NotificationResponse::from).toList());
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ApiResponse.ok();
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return ApiResponse.ok();
    }
}
