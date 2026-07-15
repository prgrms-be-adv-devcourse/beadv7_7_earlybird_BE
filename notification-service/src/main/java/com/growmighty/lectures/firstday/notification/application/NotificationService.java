package com.growmighty.lectures.firstday.notification.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.notification.domain.Notification;
import com.growmighty.lectures.firstday.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    /** TODO(팀): 지금은 HTTP 로 직접 생성하지만, 최종적으로는 이벤트 구독으로 생성되어야 한다 */
    @Transactional
    public Notification create(Long userId, String type, String message) {
        return notificationRepository.save(Notification.create(userId, type, message));
    }

    @Transactional(readOnly = true)
    public List<Notification> getByUser(Long userId) {
        return notificationRepository.findByUserId(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.findByUserId(userId).forEach(Notification::markAsRead);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 알림입니다. notificationId=" + notificationId));
        notification.markAsRead();
    }
}
