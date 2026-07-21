package com.growmighty.lectures.firstday.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 알림 서비스 — 이벤트 구독형.
 * TODO(팀): 결제 완료·마감 판정·새 소식·정산 완료 이벤트 구독 방식 결정
 *           (Spring Event 는 프로세스 내부 전용 — 서비스 간에는 Kafka 또는 HTTP 필요)
 */
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.notification",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
