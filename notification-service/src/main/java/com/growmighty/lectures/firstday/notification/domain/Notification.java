package com.growmighty.lectures.firstday.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** TODO(팀): 알림 유형 enum 확정 (PAYMENT_COMPLETED, PROJECT_CLOSED, NOTICE_PUBLISHED, SETTLEMENT_COMPLETED...) */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Notification(Long userId, String type, String message) {
        this.userId = userId;
        this.type = type;
        this.message = message;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public static Notification create(Long userId, String type, String message) {
        return new Notification(userId, type, message);
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
