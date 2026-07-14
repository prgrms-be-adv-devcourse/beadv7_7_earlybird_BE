package com.growmighty.lectures.firstday.board;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 커뮤니티 서비스 — 하나의 서비스가 세 도메인을 가진다 (팀 설계 결정):
 * 창작자 공지(notice) / 사용자 의견·문의(comment) / 리뷰(review).
 * 게시판형과 리뷰형은 DB 구조가 달라 도메인을 분리하되, DB(스키마)는 서비스 단위로 갖는다.
 */
@SpringBootApplication
public class BoardServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoardServiceApplication.class, args);
    }
}
