package com.growmighty.lectures.firstday.project.domain;

import java.util.List;
import java.util.Optional;

public interface RewardRepository {
    Reward save(Reward reward);

    Optional<Reward> findById(Long id);

    List<Reward> findByProjectId(Long projectId);
}
