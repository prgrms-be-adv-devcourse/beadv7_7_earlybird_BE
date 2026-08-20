package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.user.domain.CreatorApplication;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationRepository;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CreatorApplicationRepositoryAdapter implements CreatorApplicationRepository {
    private final CreatorApplicationJpaRepository jpaRepository;

    @Override
    public CreatorApplication save(CreatorApplication application) {
        return jpaRepository.save(application);
    }

    @Override
    public Optional<CreatorApplication> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<CreatorApplication> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<CreatorApplication> findAllByStatus(CreatorApplicationStatus status) {
        return jpaRepository.findAllByStatus(status);
    }

    @Override
    public boolean existsByUserIdAndStatus(Long userId, CreatorApplicationStatus status) {
        return jpaRepository.existsByUserIdAndStatus(userId, status);
    }
}
