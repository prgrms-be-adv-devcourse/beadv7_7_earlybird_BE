package com.growmighty.lectures.firstday.settlement.presentation;

import com.growmighty.lectures.firstday.settlement.application.CreatorProjectSettlementQueryService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.CreatorProjectSettlementDetailResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.CreatorProjectSettlementListItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class CreatorProjectSettlementQueryController {

    private final CreatorProjectSettlementQueryService queryService;

    @GetMapping
    public List<CreatorProjectSettlementListItemResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return queryService.findAll(Long.valueOf(jwt.getSubject())).stream()
                .map(CreatorProjectSettlementListItemResponse::from)
                .toList();
    }

    @GetMapping("/{settlementId}")
    public CreatorProjectSettlementDetailResponse findDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long settlementId
    ) {
        return CreatorProjectSettlementDetailResponse.from(
                queryService.findDetail(Long.valueOf(jwt.getSubject()), settlementId)
        );
    }
}
