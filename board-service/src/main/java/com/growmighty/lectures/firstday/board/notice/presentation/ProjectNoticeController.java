package com.growmighty.lectures.firstday.board.notice.presentation;

import com.growmighty.lectures.firstday.board.notice.application.ProjectNoticeService;
import com.growmighty.lectures.firstday.board.notice.application.dto.DeleteProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.RegisterProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.UpdateProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.presentation.dto.ProjectNoticeRequest;
import com.growmighty.lectures.firstday.board.notice.presentation.dto.ProjectNoticeResponse;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 창작자 공지(새 소식) API. TODO(팀): 인증 도입 후 창작자 본인 검증 추가 */
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class ProjectNoticeController {
    private final ProjectNoticeService noticeService;

    @PostMapping
    public ProjectNoticeResponse register(@RequestParam Long projectId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                    @Valid @RequestBody ProjectNoticeRequest request) {
        return ProjectNoticeResponse.from(
            noticeService.register(new RegisterProjectNoticeCommand(projectId, authorId, request.title(), request.content())));
    }

    @GetMapping
    public List<ProjectNoticeResponse> getByProject(@RequestParam Long projectId) {
        return ProjectNoticeResponse.from(noticeService.getByProject(projectId));
    }

    @GetMapping("/{noticeId}")
    public ProjectNoticeResponse getNotice(@PathVariable Long noticeId) {
        return ProjectNoticeResponse.from(noticeService.getNotice(noticeId));
    }

    @PatchMapping("/{noticeId}")
    public ProjectNoticeResponse update(@PathVariable Long noticeId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                  @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                  @Valid @RequestBody ProjectNoticeRequest request) {
        return ProjectNoticeResponse.from(
            noticeService.update(new UpdateProjectNoticeCommand(noticeId, requesterId, requesterRole, request.title(), request.content())));
    }

    @DeleteMapping("/{noticeId}")
    public Void delete(@PathVariable Long noticeId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        noticeService.delete(new DeleteProjectNoticeCommand(noticeId, requesterId, requesterRole));
        return null;
    }
}
