package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.user.domain.CreatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreatorProfileJpaRepository extends JpaRepository<CreatorProfile, Long> {
    Optional<CreatorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
