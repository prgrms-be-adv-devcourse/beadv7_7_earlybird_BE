package com.growmighty.lectures.firstday.board.review.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 리뷰 — 리워드 수령 후원자의 평가. 게시판형과 DB 구조가 달라 별도 도메인으로 분리 (팀 설계 결정).
 */
@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long authorId;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "rating", nullable = false))
    private Rating rating;

    @Lob
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    private Review(Long projectId, Long orderId, Long authorId, Rating rating, String content) {
        validateProjectId(projectId);
        validateOrderId(orderId);
        validateAuthorId(authorId);

        this.projectId = projectId;
        this.orderId = orderId;
        this.authorId = authorId;
        this.rating = rating;
        this.content = content;
        this.status = ReviewStatus.ACTIVE;
    }

    public static Review create(Long projectId, Long orderId, Long authorId, BigDecimal rating, String content) {
        return new Review(projectId, orderId, authorId, Rating.from(rating), content);
    }

    public void update(Long requesterId, BigDecimal newRating, String newContent) {
        validateNotDeleted();
        validateAuthorId(requesterId);
        validateAuthorOnly(requesterId);

        this.rating = Rating.from(newRating);
        this.content = newContent;
        this.status = ReviewStatus.MODIFIED;
    }

    public void delete(Long requesterId, UserRole requesterRole) {
        validateNotDeleted();
        validateAuthorId(requesterId);
        validateOwnership(requesterId, requesterRole);
        this.status = ReviewStatus.DELETED;
    }

    private void validateNotDeleted() {
        if(this.status == ReviewStatus.DELETED) {
            throw new IllegalStateException("이미 삭제된 리뷰입니다.");
        }
    }

    private void validateOwnership(Long requesterId, UserRole requesterRole) {
        if (requesterRole == UserRole.ADMIN) {
            return;
        }
        if (!requesterId.equals(this.authorId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
    }

    private void validateAuthorOnly(Long requesterId) {
        if (!requesterId.equals(this.authorId)) {
            throw new IllegalArgumentException("작성자만 수정할 수 있습니다.");
        }
    }

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("프로젝트 ID는 필수입니다.");
        }
    }

    private void validateAuthorId(Long requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("작성자 정보를 불러올 수 없습니다.");
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 필수입니다.");
        }
    }
}
