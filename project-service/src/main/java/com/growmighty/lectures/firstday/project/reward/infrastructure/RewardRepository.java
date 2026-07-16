package com.growmighty.lectures.firstday.project.reward.infrastructure;

import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardRepository extends JpaRepository<Reward, Long> {
    List<Reward> findByProjectId(Long projectId);
}
