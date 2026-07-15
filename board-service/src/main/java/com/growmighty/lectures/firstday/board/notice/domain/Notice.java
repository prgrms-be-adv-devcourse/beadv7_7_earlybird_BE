package com.growmighty.lectures.firstday.board.notice.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 창작자 공지(새 소식) — 프로젝트에 달리는 업데이트 게시글. 프로젝트와는 ID로만 참조한다. */
@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    @Lob
    private String content;

    private Notice(Long projectId, String title, String content) {
        this.projectId = projectId;
        this.title = title;
        this.content = content;
    }

    public static Notice create(Long projectId, String title, String content) {
        // TODO(팀): 작성자가 해당 프로젝트의 창작자인지 검증 (인증 도입 후)
        return new Notice(projectId, title, content);
    }

    public void update(String title, String content) {
        // TODO(팀): 수정 이력 보관 여부 결정
        this.title = title;
        this.content = content;
    }
}
