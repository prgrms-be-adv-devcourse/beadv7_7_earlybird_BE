package com.growmighty.lectures.firstday.user.domain;

import java.util.List;
import java.util.Optional;

public interface CreatorApplicationRepository {
    CreatorApplication save(CreatorApplication application);

    Optional<CreatorApplication> findById(Long id);

    List<CreatorApplication> findAll();

    List<CreatorApplication> findAllByStatus(CreatorApplicationStatus status);

    boolean existsByUserIdAndStatus(Long userId, CreatorApplicationStatus status);
}
