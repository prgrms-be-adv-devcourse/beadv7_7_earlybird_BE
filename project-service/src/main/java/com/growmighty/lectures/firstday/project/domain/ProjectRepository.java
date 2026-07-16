package com.growmighty.lectures.firstday.project.domain;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    Project save(Project project);

    Optional<Project> findById(Long id);

    List<Project> findAll();      // ★ 추가 — 재색인용
}
