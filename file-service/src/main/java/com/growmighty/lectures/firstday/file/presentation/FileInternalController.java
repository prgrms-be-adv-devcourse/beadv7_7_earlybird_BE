package com.growmighty.lectures.firstday.file.presentation;

import com.growmighty.lectures.firstday.file.application.FileService;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.presentation.dto.FileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 서비스 간 내부 API — 게이트웨이 라우트가 없다(Eureka-to-Eureka 직접 호출 전용, CLAUDE.md 참고).
 * board-service가 후기 목록의 첨부 사진을 N+1 없이 붙일 때 호출한다.
 *
 * <p>owner 단위 일괄 삭제(DELETE)는 여기 없다 — 프로젝트 삭제 시 파일 정리가 동기 HTTP에서
 * Kafka({@code project.deleted.v1}, #690)로 전환되면서 호출자가 사라졌고, 인증 없이 호출 가능한
 * 삭제 경로를 남겨둘 이유가 없어 제거했다. 실제 삭제는
 * {@code ProjectDeletedKafkaListener} → {@code FileService.deleteByOwner}가 처리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/files")
public class FileInternalController {
    private final FileService fileService;

    @GetMapping("/batch")
    public List<FileResponse> getFilesByOwners(@RequestParam FileOwnerType ownerType, @RequestParam List<Long> ownerIds) {
        return fileService.getFilesByOwners(ownerType, ownerIds).stream()
            .map(FileResponse::from)
            .toList();
    }
}
