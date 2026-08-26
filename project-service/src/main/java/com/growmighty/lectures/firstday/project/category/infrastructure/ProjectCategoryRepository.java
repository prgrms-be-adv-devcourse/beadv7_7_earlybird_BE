package com.growmighty.lectures.firstday.project.category.infrastructure;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectCategoryRepository extends JpaRepository<ProjectCategory, Long> {

    boolean existsByParentProjectCategoryId(Long parentProjectCategoryId);

    List<ProjectCategory> findByNameIgnoreCase(String name);
}
