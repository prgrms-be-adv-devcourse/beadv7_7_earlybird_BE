package com.growmighty.lectures.firstday.project.infrastructure;

import com.growmighty.lectures.firstday.project.domain.Reward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardJpaRepository extends JpaRepository<Reward, Long> {
    List<Reward> findByProjectId(Long projectId);
}
