package com.growmighty.lectures.firstday.file.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.file.application.dto.FileInfo;
import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadCommand;
import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadInfo;
import com.growmighty.lectures.firstday.file.application.dto.RegisterFileCommand;
import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.domain.FileRepository;
import com.growmighty.lectures.firstday.file.infrastructure.S3PresignedUploadGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileService {
    private final FileRepository fileRepository;
    private final S3PresignedUploadGenerator presignedUploadGenerator;

    public PresignedUploadInfo issuePresignedUpload(PresignedUploadCommand command) {
        return presignedUploadGenerator.generate(command.requesterId(), command.contentType(), command.originalName());
    }

    @Transactional
    public FileInfo register(RegisterFileCommand command) {
        File file = File.register(
            command.ownerType(), command.ownerId(), command.storedUrl(), command.originalName(),
            command.contentType(), command.fileSize(), command.sortOrder());
        return FileInfo.from(fileRepository.save(file));
    }

    @Transactional(readOnly = true)
    public List<FileInfo> getFilesByOwner(FileOwnerType ownerType, Long ownerId) {
        return fileRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId).stream()
            .map(FileInfo::from)
            .toList();
    }

    @Transactional
    public void delete(Long fileId) {
        fileRepository.findById(fileId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 파일입니다. fileId=" + fileId));
        fileRepository.deleteById(fileId);
    }
}
