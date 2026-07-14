package com.growmighty.lectures.firstday.notification.infrastructure;

import com.growmighty.lectures.firstday.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserId(Long userId);
}
