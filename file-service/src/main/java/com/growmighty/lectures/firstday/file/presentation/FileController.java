package com.growmighty.lectures.firstday.file.presentation;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.file.application.FileService;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.presentation.dto.FileResponse;
import com.growmighty.lectures.firstday.file.presentation.dto.PresignedUploadRequest;
import com.growmighty.lectures.firstday.file.presentation.dto.PresignedUploadResponse;
import com.growmighty.lectures.firstday.file.presentation.dto.RegisterFileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 파일 메타데이터 API. 업로드는 presigned URL 로 클라이언트가 스토리지에 직접 올리고,
 * 성공 후 register 로 메타데이터만 등록한다 (소유권 연결은 register 가 맡음).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService fileService;

    @PostMapping("/presigned-upload")
    public PresignedUploadResponse presign(
            @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
            @Valid @RequestBody PresignedUploadRequest request
    ) {
        return PresignedUploadResponse.from(fileService.issuePresignedUpload(request.toCommand(requesterId)));
    }

    @PostMapping
    public FileResponse register(
            @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
            @RequestBody RegisterFileRequest request
    ) {
        return FileResponse.from(fileService.register(request.toCommand(requesterId)));
    }

    @GetMapping
    public List<FileResponse> getFilesByOwner(@RequestParam FileOwnerType ownerType, @RequestParam Long ownerId) {
        return fileService.getFilesByOwner(ownerType, ownerId).stream()
                .map(FileResponse::from)
                .toList();
    }

    @DeleteMapping("/{fileId}")
    public Void delete(@PathVariable Long fileId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId) {
        fileService.delete(fileId, requesterId);
        return null;
    }
}
