package com.growmighty.lectures.firstday.project.infrastructure;

import com.growmighty.lectures.firstday.project.domain.ProjectCategory;
import com.growmighty.lectures.firstday.project.domain.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectCategoryRepositoryAdapter implements ProjectCategoryRepository {
    private final ProjectCategoryJpaRepository jpaRepository;

    @Override
    public ProjectCategory save(ProjectCategory projectCategory) {
        return jpaRepository.save(projectCategory);
    }

    @Override
    public Optional<ProjectCategory> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<ProjectCategory> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }
}
