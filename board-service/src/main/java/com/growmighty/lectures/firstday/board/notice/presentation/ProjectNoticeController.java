package com.growmighty.lectures.firstday.board.notice.presentation;

import com.growmighty.lectures.firstday.board.notice.application.ProjectNoticeService;
import com.growmighty.lectures.firstday.board.notice.application.dto.DeleteProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.RegisterProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.application.dto.UpdateProjectNoticeCommand;
import com.growmighty.lectures.firstday.board.notice.presentation.dto.ProjectNoticeRequest;
import com.growmighty.lectures.firstday.board.notice.presentation.dto.ProjectNoticeResponse;
import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 창작자 공지(새 소식) API. TODO(팀): 인증 도입 후 창작자 본인 검증 추가 */
@RestController
@RequiredArgsConstructor
public class ProjectNoticeController {
    private final ProjectNoticeService noticeService;

    @PostMapping("/projects/{projectId}/notices")
    public ProjectNoticeResponse register(@PathVariable Long projectId, @RequestHeader(JwtHeaders.USER_ID) Long authorId,
                                    @RequestBody ProjectNoticeRequest request) {
        return ProjectNoticeResponse.from(
            noticeService.register(new RegisterProjectNoticeCommand(projectId, authorId, request.authorName(), request.title(), request.content())));
    }

    @GetMapping("/projects/{projectId}/notices")
    public List<ProjectNoticeResponse> getByProject(@PathVariable Long projectId) {
        return noticeService.getByProject(projectId).stream().map(ProjectNoticeResponse::from).toList();
    }

    // projectId는 프론트가 이미 같은 페이지 컨텍스트에서 들고 있는 값을 URL 구조상으로만 맞춘 것 — 별도 검증 없이 noticeId로 단건을 특정한다.
    @GetMapping("/projects/{projectId}/notices/{noticeId}")
    public ProjectNoticeResponse getNotice(@PathVariable Long noticeId) {
        return ProjectNoticeResponse.from(noticeService.getNotice(noticeId));
    }

    @PatchMapping("/projects/{projectId}/notices/{noticeId}")
    public ProjectNoticeResponse update(@PathVariable Long noticeId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                                  @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                  @RequestBody ProjectNoticeRequest request) {
        return ProjectNoticeResponse.from(
            noticeService.update(new UpdateProjectNoticeCommand(noticeId, requesterId, requesterRole, request.title(), request.content())));
    }

    @DeleteMapping("/projects/{projectId}/notices/{noticeId}")
    public Void delete(@PathVariable Long noticeId, @RequestHeader(JwtHeaders.USER_ID) Long requesterId,
                        @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole) {
        noticeService.delete(new DeleteProjectNoticeCommand(noticeId, requesterId, requesterRole));
        return null;
    }
}
