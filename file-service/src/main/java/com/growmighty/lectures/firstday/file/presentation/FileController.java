package com.growmighty.lectures.firstday.file.presentation;

import com.growmighty.lectures.firstday.file.application.FileService;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.presentation.dto.FileResponse;
import com.growmighty.lectures.firstday.file.presentation.dto.RegisterFileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 파일 메타데이터 API. 실제 업로드(저장소 연동)는 범위 밖 — storedUrl 은 클라이언트가
 * 이미 업로드를 마친 경로/URL 이라고 가정하고 그 메타데이터만 등록한다.
 * TODO(팀): 업로드 방식(직접 업로드 vs presigned URL) 확정 필요.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {
    private final FileService fileService;

    @PostMapping
    public FileResponse register(@RequestBody RegisterFileRequest request) {
        return FileResponse.from(fileService.register(request.toCommand()));
    }

    @GetMapping
    public List<FileResponse> getFilesByOwner(@RequestParam FileOwnerType ownerType, @RequestParam Long ownerId) {
        return fileService.getFilesByOwner(ownerType, ownerId).stream()
                .map(FileResponse::from)
                .toList();
    }

    @DeleteMapping("/{fileId}")
    public Void delete(@PathVariable Long fileId) {
        fileService.delete(fileId);
        return null;
    }
}
