package com.growmighty.lectures.firstday.project.category.infrastructure;

import com.growmighty.lectures.firstday.project.category.domain.ProjectCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCategoryRepository extends JpaRepository<ProjectCategory, Long> {
}
