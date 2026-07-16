package com.growmighty.lectures.firstday.project.infrastructure;

import com.growmighty.lectures.firstday.project.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJpaRepository extends JpaRepository<Project, Long> {
}
