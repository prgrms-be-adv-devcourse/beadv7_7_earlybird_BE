package com.growmighty.lectures.firstday.board.comment.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 의견·문의 — project 본문/ProjectNotice/Review에 공통으로 달리는 댓글. 대상은 (targetType, targetId) 쌍으로만 참조한다. */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommentTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private Long authorId;

    /** 답글(창작자 응대 포함)용 부모 댓글. 대댓글은 1단까지만 허용(답글에는 답글 불가). */
    private Long parentId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    private CommentStatus status;

    private Comment(CommentTargetType targetType, Long targetId, Long authorId, Long parentId, String content) {
        validateTargetType(targetType);
        validateTargetId(targetId);
        validateAuthorId(authorId);
        validateContent(content);

        this.targetType = targetType;
        this.targetId = targetId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.content = content;
        this.status = CommentStatus.ACTIVE;
    }

    public static Comment create(CommentTargetType targetType, Long targetId, Long authorId, String content) {
        return new Comment(targetType, targetId, authorId, null, content);
    }

    public static Comment reply(Comment parent, Long authorId, String content) {
        parent.validateReplyable();
        return new Comment(parent.targetType, parent.targetId, authorId, parent.id, content);
    }

    private void validateReplyable() {
        if (this.parentId != null) {
            throw new IllegalArgumentException("대댓글에는 답글을 달 수 없습니다.");
        }
    }

    public void update(Long requesterId, String content) {
        validateNotDeleted();
        validateAuthorId(requesterId);
        validateAuthorOnly(requesterId);
        validateContent(content);

        this.content = content;
        this.status = CommentStatus.MODIFIED;
    }

    public void delete(Long requesterId, UserRole requesterRole) {
        validateNotDeleted();
        validateAuthorId(requesterId);
        validateOwnership(requesterId, requesterRole);

        this.status = CommentStatus.DELETED;
    }

    private void validateAuthorOnly(Long requesterId) {
        if(!requesterId.equals(this.authorId)) {
            throw new IllegalArgumentException("작성자만 수정할 수 있습니다.");
        }
    }

    private void validateNotDeleted() {
        if (this.status == CommentStatus.DELETED) {
            throw new IllegalStateException("이미 삭제된 댓글입니다.");
        }
    }

    private void validateOwnership(Long requesterId, UserRole requesterRole) {
        if(requesterRole == UserRole.ADMIN) {
            return;
        }
        if(!requesterId.equals(this.authorId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }
    }

    private void validateTargetType(CommentTargetType targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("댓글 대상 타입은 필수입니다.");
        }
    }

    private void validateTargetId(Long targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("댓글 대상 ID는 필수입니다.");
        }
    }

    private void validateAuthorId(Long authorId) {
        if (authorId == null) {
            throw new IllegalArgumentException("작성자 정보를 불러올 수 없습니다.");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용은 비어 있을 수 없습니다.");
        }
    }
}
