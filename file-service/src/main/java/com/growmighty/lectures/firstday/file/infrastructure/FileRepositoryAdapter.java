package com.growmighty.lectures.firstday.file.infrastructure;

import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.domain.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FileRepositoryAdapter implements FileRepository {
    private final FileJpaRepository jpaRepository;

    @Override
    public File save(File file) {
        return jpaRepository.save(file);
    }

    @Override
    public Optional<File> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<File> findByOwnerTypeAndOwnerId(FileOwnerType ownerType, Long ownerId) {
        return jpaRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteByOwnerTypeAndOwnerId(FileOwnerType ownerType, Long ownerId) {
        jpaRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
    }
}
