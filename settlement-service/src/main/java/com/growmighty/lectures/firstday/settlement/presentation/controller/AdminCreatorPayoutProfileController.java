package com.growmighty.lectures.firstday.settlement.presentation.controller;

import com.growmighty.lectures.firstday.common.entity.UserRole;
import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.settlement.application.payout.CreatorPayoutProfileRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements/creator-payout-profiles")
@RequiredArgsConstructor
public class AdminCreatorPayoutProfileController {

    private final CreatorPayoutProfileRegistrationService registrationService;

    @PostMapping("/{creatorId}/registration")
    public ResponseEntity<Void> register(
            @RequestHeader(JwtHeaders.USER_ROLE) UserRole requesterRole,
            @PathVariable Long creatorId
    ) {
        if (requesterRole != UserRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "관리자만 접근할 수 있습니다.");
        }
        registrationService.registerByAdmin(creatorId);
        return ResponseEntity.ok().build();
    }
}
