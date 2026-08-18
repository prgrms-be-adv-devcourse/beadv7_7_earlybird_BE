package com.growmighty.lectures.firstday.file.domain;

import java.util.List;
import java.util.Optional;

public interface FileRepository {
    File save(File file);

    Optional<File> findById(Long id);

    List<File> findByOwnerTypeAndOwnerId(FileOwnerType ownerType, Long ownerId);

    void deleteById(Long id);

    void deleteByOwnerTypeAndOwnerId(FileOwnerType ownerType, Long ownerId);
}
