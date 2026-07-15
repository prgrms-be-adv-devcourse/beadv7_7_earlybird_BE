package com.growmighty.lectures.firstday.board.notice.presentation;

import com.growmighty.lectures.firstday.board.notice.application.NoticeService;
import com.growmighty.lectures.firstday.board.notice.presentation.dto.NoticeRequest;
import com.growmighty.lectures.firstday.board.notice.presentation.dto.NoticeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 창작자 공지(새 소식) API. TODO(팀): 인증 도입 후 창작자 본인 검증 추가 */
@RestController
@RequiredArgsConstructor
public class NoticeController {
    private final NoticeService noticeService;

    @PostMapping("/projects/{projectId}/notices")
    public NoticeResponse register(@PathVariable Long projectId, @RequestBody NoticeRequest request) {
        return NoticeResponse.from(noticeService.register(projectId, request.title(), request.content()));
    }

    @GetMapping("/projects/{projectId}/notices")
    public List<NoticeResponse> getByProject(@PathVariable Long projectId) {
        return noticeService.getByProject(projectId).stream().map(NoticeResponse::from).toList();
    }

    @PatchMapping("/notices/{noticeId}")
    public NoticeResponse update(@PathVariable Long noticeId, @RequestBody NoticeRequest request) {
        return NoticeResponse.from(noticeService.update(noticeId, request.title(), request.content()));
    }

    @DeleteMapping("/notices/{noticeId}")
    public Void delete(@PathVariable Long noticeId) {
        noticeService.delete(noticeId);
        return null;
    }
}
