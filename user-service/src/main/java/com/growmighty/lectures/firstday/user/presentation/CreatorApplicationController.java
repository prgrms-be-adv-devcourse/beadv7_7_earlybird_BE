package com.growmighty.lectures.firstday.user.presentation;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.user.application.CreatorApplicationService;
import com.growmighty.lectures.firstday.user.domain.CreatorApplicationStatus;
import com.growmighty.lectures.firstday.user.presentation.dto.ApplyCreatorApplicationRequest;
import com.growmighty.lectures.firstday.user.presentation.dto.CreatorApplicationResponse;
import com.growmighty.lectures.firstday.user.presentation.dto.RejectCreatorApplicationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 창작자 전환 신청 및 관리자 심사(#448). 신청 즉시 role이 바뀌는 {@code /me/creator}(발표용 즉시 등록)와
 * 달리, 여기서는 관리자 승인을 거쳐야 role이 CREATOR로 바뀐다. gateway(SecurityConfig)가 라우트 단위로
 * ADMIN role을 걸러내지만, 다른 관리자 전용 엔드포인트(ProjectController 등)와 같은 컨벤션으로
 * 컨트롤러에서도 X-User-Role 헤더를 한 번 더 검증한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class CreatorApplicationController {
    private final CreatorApplicationService creatorApplicationService;

    /** 창작자 전환 신청. userId 는 gateway 가 검증한 JWT 에서 추출해 X-User-Id 헤더로 전달한다. */
    @PostMapping("/me/creator-application")
    public CreatorApplicationResponse apply(@RequestHeader(JwtHeaders.USER_ID) Long userId,
                                             @Valid @RequestBody ApplyCreatorApplicationRequest request) {
        return CreatorApplicationResponse.from(creatorApplicationService.apply(request.toCommand(userId)));
    }

    /** 관리자 전용: 창작자 전환 신청 목록 조회. status 미지정 시 전체를 반환한다. */
    @GetMapping("/creator-applications")
    public List<CreatorApplicationResponse> findAll(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                                      @RequestParam(required = false) CreatorApplicationStatus status) {
        requireAdmin(requesterRole);
        return creatorApplicationService.findAll(status).stream()
                .map(CreatorApplicationResponse::from)
                .toList();
    }

    /** 관리자 전용: 승인 (PENDING → APPROVED) — user role을 CREATOR로 전환하고 creator_profiles를 생성한다. */
    @PostMapping("/creator-applications/{applicationId}/approve")
    public CreatorApplicationResponse approve(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                               @PathVariable Long applicationId) {
        requireAdmin(requesterRole);
        return CreatorApplicationResponse.from(creatorApplicationService.approve(applicationId));
    }

    /** 관리자 전용: 반려 (PENDING → REJECTED) — rejectReason을 기록한다. */
    @PostMapping("/creator-applications/{applicationId}/reject")
    public CreatorApplicationResponse reject(@RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
                                              @PathVariable Long applicationId,
                                              @Valid @RequestBody RejectCreatorApplicationRequest request) {
        requireAdmin(requesterRole);
        return CreatorApplicationResponse.from(creatorApplicationService.reject(request.toCommand(applicationId)));
    }

    private void requireAdmin(UserRole requesterRole) {
        if (requesterRole != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
    }
}
