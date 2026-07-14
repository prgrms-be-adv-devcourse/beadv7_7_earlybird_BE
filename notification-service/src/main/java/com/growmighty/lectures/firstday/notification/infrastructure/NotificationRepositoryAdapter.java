package com.growmighty.lectures.firstday.notification.infrastructure;

import com.growmighty.lectures.firstday.notification.domain.Notification;
import com.growmighty.lectures.firstday.notification.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepository {
    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notification> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }
}
