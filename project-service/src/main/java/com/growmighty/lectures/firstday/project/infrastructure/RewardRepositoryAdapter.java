package com.growmighty.lectures.firstday.project.infrastructure;

import com.growmighty.lectures.firstday.project.domain.Reward;
import com.growmighty.lectures.firstday.project.domain.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RewardRepositoryAdapter implements RewardRepository {
    private final RewardJpaRepository jpaRepository;

    @Override
    public Reward save(Reward reward) {
        return jpaRepository.save(reward);
    }

    @Override
    public Optional<Reward> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Reward> findByProjectId(Long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }
}
