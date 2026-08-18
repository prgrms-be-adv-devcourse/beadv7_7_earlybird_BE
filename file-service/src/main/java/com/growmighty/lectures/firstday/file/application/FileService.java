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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FileService {
    private final FileRepository fileRepository;
    private final S3PresignedUploadGenerator presignedUploadGenerator;
    private final ProjectPort projectPort;
    private final String cdnBaseUrl;

    public FileService(
            FileRepository fileRepository,
            S3PresignedUploadGenerator presignedUploadGenerator,
            ProjectPort projectPort,
            @Value("${aws.s3.cdn-base-url}") String cdnBaseUrl
    ) {
        this.fileRepository = fileRepository;
        this.presignedUploadGenerator = presignedUploadGenerator;
        this.projectPort = projectPort;
        this.cdnBaseUrl = cdnBaseUrl;
    }

    public PresignedUploadInfo issuePresignedUpload(PresignedUploadCommand command) {
        return presignedUploadGenerator.generate(command.requesterId(), command.contentType(), command.originalName());
    }

    @Transactional
    public FileInfo register(RegisterFileCommand command) {
        validateStoredUrlOrigin(command);
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

    // storedUrl을 검증 없이 그대로 저장하면, 본인이 presign 받아 실제로 올린 오브젝트가 아니라
    // 임의의 외부 URL(예: 악성 사이트)을 등록해버릴 수 있다 — presign이 발급하는 키 형식
    // (files/{requesterId}/...)에 맞는 storedUrl만 허용한다.
    private void validateStoredUrlOrigin(RegisterFileCommand command) {
        String expectedPrefix = cdnBaseUrl + "/files/" + command.uploaderId() + "/";
        if (!command.storedUrl().startsWith(expectedPrefix)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                "본인이 presign 발급받아 업로드한 storedUrl만 등록할 수 있습니다.");
        }
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
