package com.growmighty.lectures.firstday.project.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 카테고리 — 대분류/소분류 2단계를 자기참조로 표현한다.
 * parentId == null 이면 1차(대분류), 값이 있으면 2차(소분류) — 2단계로 고정한다.
 * 시드 데이터로 고정되며 관리자만 추가한다. 프로젝트는 2차(소분류)만 지정한다.
 */
@Entity
@Table(name = "project_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCategory extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null = 1차(대분류), 값 있음 = 2차(소분류). 같은 DB의 자기참조지만 계층이라 FK 대신 논리 참조로 둔다. */
    @Column
    private Long parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer displayOrder;

    private ProjectCategory(Long parentId, String name, int displayOrder) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("카테고리 이름은 필수입니다.");
        }
        this.parentId = parentId;
        this.name = name;
        this.displayOrder = displayOrder;
    }

    public static ProjectCategory createRoot(String name, int displayOrder) {
        return new ProjectCategory(null, name, displayOrder);
    }

    public static ProjectCategory createChild(Long parentId, String name, int displayOrder) {
        if (parentId == null) {
            throw new IllegalArgumentException("소분류는 parentId가 필수입니다.");
        }
        return new ProjectCategory(parentId, name, displayOrder);
    }

    public boolean isRoot() {
        return this.parentId == null;
    }
}
