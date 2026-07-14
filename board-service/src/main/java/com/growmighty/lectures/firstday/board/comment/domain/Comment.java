package com.growmighty.lectures.firstday.board.comment.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 사용자 의견·문의 — 프로젝트에 달리는 댓글형 게시글. 프로젝트/유저와는 ID로만 참조한다. */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long userId;

    /** 답글(창작자 응대 포함)용 부모 댓글. TODO(팀): 대댓글 깊이 제한·정렬 정책 결정 */
    private Long parentId;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Comment(Long projectId, Long userId, Long parentId, String content) {
        this.projectId = projectId;
        this.userId = userId;
        this.parentId = parentId;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public static Comment create(Long projectId, Long userId, Long parentId, String content) {
        return new Comment(projectId, userId, parentId, content);
    }
}
