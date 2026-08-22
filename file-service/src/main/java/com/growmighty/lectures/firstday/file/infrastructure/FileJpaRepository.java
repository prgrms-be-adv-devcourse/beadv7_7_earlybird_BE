package com.growmighty.lectures.firstday.file.infrastructure;

import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileJpaRepository extends JpaRepository<File, Long> {
    List<File> findByOwnerTypeAndOwnerId(FileOwnerType ownerType, Long ownerId);

    List<File> findByOwnerTypeAndOwnerIdIn(FileOwnerType ownerType, List<Long> ownerIds);

    void deleteByOwnerTypeAndOwnerId(FileOwnerType ownerType, Long ownerId);
}
