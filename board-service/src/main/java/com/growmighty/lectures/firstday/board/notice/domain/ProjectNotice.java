package com.growmighty.lectures.firstday.board.notice.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectNotice extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String authorName;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Long viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectNoticeStatus status;

    private ProjectNotice(Long projectId, Long authorId, String authorName, String title, String content) {
        validateProjectId(projectId);
        validateTitle(title);
        validateContent(content);
        validateAuthorId(authorId);
        validateAuthorName(authorName);

        this.projectId = projectId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.title = title;
        this.content = content;

        this.status = ProjectNoticeStatus.ACTIVE;
        this.viewCount = 0L;
    }

    public static ProjectNotice create(Long projectId, Long authorId, String authorName, String title, String content) {
        return new ProjectNotice(projectId, authorId, authorName, title, content);
    }

    public void update(Long requesterId, UserRole requesterRole, String title, String content) {
        validateNotDeleted();
        validateAuthorId(requesterId);
        validateOwnership(requesterId, requesterRole);
        validateTitle(title);
        validateContent(content);
        this.title = title;
        this.content = content;
        this.status = ProjectNoticeStatus.MODIFIED;
    }

    public void delete(Long requesterId, UserRole requesterRole) {
        validateNotDeleted();
        validateAuthorId(requesterId);
        validateOwnership(requesterId, requesterRole);
        this.status = ProjectNoticeStatus.DELETED;
    }

    private void validateNotDeleted() {
        if (this.status == ProjectNoticeStatus.DELETED) {
            throw new IllegalStateException("이미 삭제된 공지글입니다.");
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

    private void validateProjectId(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("프로젝트 ID는 필수입니다.");
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목은 비어 있을 수 없습니다.");
        }

        if(title.length() > 255) {
            throw new IllegalArgumentException("제목은 255자를 넘길 수 없습니다.");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("내용은 비어 있을 수 없습니다.");
        }
    }

    private void validateAuthorId(Long requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("작성자 정보를 불러올 수 없습니다.");
        }
    }

    private void validateAuthorName(String authorName) {
        if (authorName == null || authorName.isBlank()) {
            throw new IllegalArgumentException("작성자 이름은 필수입니다.");
        }
    }
}
