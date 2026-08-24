package com.growmighty.lectures.firstday.user.infrastructure;

import com.growmighty.lectures.firstday.user.domain.CreatorApplication;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreatorApplicationJpaRepository extends JpaRepository<CreatorApplication, Long> {
    List<CreatorApplication> findAllByStatus(CreatorApplicationStatus status);

    boolean existsByUserIdAndStatus(Long userId, CreatorApplicationStatus status);
}
