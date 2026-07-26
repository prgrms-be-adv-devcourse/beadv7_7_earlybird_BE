package com.growmighty.lectures.firstday.board;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.Ordered;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 커뮤니티 서비스 — 하나의 서비스가 세 도메인을 가진다 (팀 설계 결정):
 * 창작자 공지(notice) / 사용자 의견·문의(comment) / 리뷰(review).
 * 게시판형과 리뷰형은 DB 구조가 달라 도메인을 분리하되, DB(스키마)는 서비스 단위로 갖는다.
 */
@SpringBootApplication(scanBasePackages = {
    "com.growmighty.lectures.firstday.board",
    "com.growmighty.lectures.firstday.common"   // 이게 없으면 GlobalExceptionHandler가 빈으로 안 뜬다
})
// order를 @Transactional 기본 순서(LOWEST_PRECEDENCE)보다 한 단계 높여 재시도 어드바이저가 트랜잭션을 감싸도록 한다.
// 그래야 낙관적 락 충돌로 커밋이 실패해도 재시도마다 새 트랜잭션에서 엔티티를 다시 읽는다 (project-service와 동일 관례).
@EnableRetry(order = Ordered.LOWEST_PRECEDENCE - 1)
public class BoardServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoardServiceApplication.class, args);
    }
}
