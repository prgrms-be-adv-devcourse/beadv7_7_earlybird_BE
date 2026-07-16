package com.growmighty.lectures.firstday.project.infrastructure;

import com.growmighty.lectures.firstday.project.domain.ProjectCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCategoryJpaRepository extends JpaRepository<ProjectCategory, Long> {
}
