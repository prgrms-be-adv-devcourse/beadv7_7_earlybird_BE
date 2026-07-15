package com.growmighty.lectures.firstday.user.domain;

import java.util.Optional;

public interface CreatorProfileRepository {
    CreatorProfile save(CreatorProfile creatorProfile);

    Optional<CreatorProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
