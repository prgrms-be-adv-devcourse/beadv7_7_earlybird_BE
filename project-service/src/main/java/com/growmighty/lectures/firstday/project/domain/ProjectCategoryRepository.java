package com.growmighty.lectures.firstday.project.domain;

import java.util.List;
import java.util.Optional;

public interface ProjectCategoryRepository {
    ProjectCategory save(ProjectCategory projectCategory);

    Optional<ProjectCategory> findById(Long id);

    List<ProjectCategory> findAll();

    boolean existsById(Long id);
}
