package com.growmighty.lectures.firstday.file.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.file.application.dto.FileInfo;
import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadCommand;
import com.growmighty.lectures.firstday.file.application.dto.PresignedUploadInfo;
import com.growmighty.lectures.firstday.file.application.dto.RegisterFileCommand;
import com.growmighty.lectures.firstday.file.application.port.ProjectPort;
import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.domain.FileRepository;
import com.growmighty.lectures.firstday.file.infrastructure.S3PresignedUploadGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FileService {
    private final FileRepository fileRepository;
    private final S3PresignedUploadGenerator presignedUploadGenerator;
    private final ProjectPort projectPort;

    public PresignedUploadInfo issuePresignedUpload(PresignedUploadCommand command) {
        return presignedUploadGenerator.generate(command.requesterId(), command.contentType(), command.originalName());
    }

    @Transactional
    public FileInfo register(RegisterFileCommand command) {
        // REVIEW는 board-service에 소유권 확인용 내부 API가 아직 없어 검증하지 못한다 (알려진 한계,
        // file-service/README.md 참고).
        if (command.ownerType() == FileOwnerType.PROJECT) {
            Long creatorId = projectPort.getCreatorId(command.ownerId());
            if (!command.uploaderId().equals(creatorId)) {
                throw new BusinessException(HttpStatus.FORBIDDEN,
                    "본인이 만든 프로젝트에만 파일을 등록할 수 있습니다. projectId=" + command.ownerId());
            }
        }
        File file = File.register(
            command.ownerType(), command.ownerId(), command.uploaderId(), command.storedUrl(), command.originalName(),
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
    public void delete(Long fileId, Long requesterId) {
        File file = fileRepository.findById(fileId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 파일입니다. fileId=" + fileId));
        if (!file.isUploadedBy(requesterId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "본인이 업로드한 파일만 삭제할 수 있습니다. fileId=" + fileId);
        }
        fileRepository.deleteById(fileId);
    }
}
