package com.growmighty.lectures.firstday.settlement.presentation.controller;

import com.growmighty.lectures.firstday.common.jwt.JwtHeaders;
import com.growmighty.lectures.firstday.settlement.application.query.CreatorProjectSettlementQueryService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.CreatorProjectSettlementDetailResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.CreatorProjectSettlementListItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class CreatorProjectSettlementQueryController {

    private final CreatorProjectSettlementQueryService queryService;

    @GetMapping
    public List<CreatorProjectSettlementListItemResponse> findAll(
            @RequestHeader(JwtHeaders.USER_ID) Long creatorId
    ) {
        return queryService.findAll(creatorId).stream()
                .map(CreatorProjectSettlementListItemResponse::from)
                .toList();
    }

    @GetMapping("/{settlementId}")
    public CreatorProjectSettlementDetailResponse findDetail(
            @RequestHeader(JwtHeaders.USER_ID) Long creatorId,
            @PathVariable Long settlementId
    ) {
        return CreatorProjectSettlementDetailResponse.from(
                queryService.findDetail(creatorId, settlementId)
        );
    }
}
