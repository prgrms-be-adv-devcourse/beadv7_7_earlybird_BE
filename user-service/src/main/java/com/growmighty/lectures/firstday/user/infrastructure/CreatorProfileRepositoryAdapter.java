package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.user.domain.CreatorProfile;
import com.growmighty.lectures.firstday.user.domain.CreatorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CreatorProfileRepositoryAdapter implements CreatorProfileRepository {
    private final CreatorProfileJpaRepository jpaRepository;

    @Override
    public CreatorProfile save(CreatorProfile creatorProfile) {
        return jpaRepository.save(creatorProfile);
    }

    @Override
    public Optional<CreatorProfile> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return jpaRepository.existsByUserId(userId);
    }
}
