package com.growmighty.lectures.firstday.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.core.Ordered;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

// JPA Auditing은 config.JpaAuditingConfig에서 별도로 켠다 — 여기 직접 붙이면 @WebMvcTest 슬라이스가 깨진다.
@SpringBootApplication(scanBasePackages = {
		"com.growmighty.lectures.firstday.project",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
// order를 @Transactional 기본 순서(LOWEST_PRECEDENCE)보다 한 단계 높여 재시도 어드바이저가 트랜잭션을 감싸도록 한다.
// 그래야 낙관적 락 충돌로 커밋이 실패해도 재시도마다 새 트랜잭션에서 엔티티를 다시 읽는다 (Reward.decreaseStock 참고).
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
@EnableScheduling
@EnableFeignClients
public class ProjectServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}

