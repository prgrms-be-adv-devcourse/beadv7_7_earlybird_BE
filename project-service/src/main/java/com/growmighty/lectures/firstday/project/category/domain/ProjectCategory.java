package com.growmighty.lectures.firstday.project.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 카테고리 = 계층형 구조 (Self-Referencing).
 * 상위 카테고리는 엔티티 연관관계가 아니라 id 값(parentProjectCategoryId)으로만 참조한다.
 */
@Entity
@Table(name = "project_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 최상위 카테고리는 null */
    @Column(name = "parent_project_category_id")
    private Long parentProjectCategoryId;

    @Column(nullable = false)
    private String name;

    private ProjectCategory(Long parentProjectCategoryId, String name) {
        validateName(name);
        this.parentProjectCategoryId = parentProjectCategoryId;
        this.name = name;
    }

    public static ProjectCategory create(Long parentProjectCategoryId, String name) {
        return new ProjectCategory(parentProjectCategoryId, name);
    }

    public void rename(String name) {
        validateName(name);
        this.name = name;
    }

    public void changeParent(Long parentProjectCategoryId) {
        this.parentProjectCategoryId = parentProjectCategoryId;
    }

    public boolean isRoot() {
        return this.parentProjectCategoryId == null;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("카테고리 이름은 비어 있을 수 없습니다.");
        }
    }
}
