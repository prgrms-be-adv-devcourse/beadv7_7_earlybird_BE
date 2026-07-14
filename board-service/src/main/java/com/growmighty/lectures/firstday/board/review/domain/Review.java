package com.growmighty.lectures.firstday.board.review.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 리뷰 — 리워드 수령 후원자의 평가. 게시판형과 DB 구조가 달라 별도 도메인으로 분리 (팀 설계 결정). */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer rating;

    @Lob
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Review(Long projectId, Long userId, Integer rating, String content) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("평점은 1~5 사이여야 합니다. 입력값: " + rating);
        }
        this.projectId = projectId;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public static Review create(Long projectId, Long userId, Integer rating, String content) {
        // TODO(팀): 리워드를 실제 수령한 후원자만 작성 가능 — order-service 확인(포트) 필요
        return new Review(projectId, userId, rating, content);
    }
}
