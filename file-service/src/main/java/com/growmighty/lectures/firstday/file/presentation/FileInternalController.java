package com.growmighty.lectures.firstday.file.presentation;

import com.growmighty.lectures.firstday.file.application.FileService;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 내부 API — 게이트웨이 라우트가 없다(Eureka-to-Eureka 직접 호출 전용, CLAUDE.md 참고).
 * project-service가 프로젝트 삭제 시 소유 파일을 정리할 때 호출한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/files")
public class FileInternalController {
    private final FileService fileService;

    @DeleteMapping
    public Void deleteByOwner(@RequestParam FileOwnerType ownerType, @RequestParam Long ownerId) {
        fileService.deleteByOwner(ownerType, ownerId);
        return null;
    }
}
