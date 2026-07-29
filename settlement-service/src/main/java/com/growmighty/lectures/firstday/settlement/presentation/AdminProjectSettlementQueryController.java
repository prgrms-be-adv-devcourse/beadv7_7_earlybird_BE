package com.growmighty.lectures.firstday.settlement.presentation;

import com.growmighty.lectures.firstday.settlement.application.AdminProjectSettlementQueryService;
import com.growmighty.lectures.firstday.settlement.presentation.dto.AdminProjectSettlementDetailResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.AdminProjectSettlementListItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements/all")
@RequiredArgsConstructor
public class AdminProjectSettlementQueryController {

    private final AdminProjectSettlementQueryService queryService;

    @GetMapping
    public List<AdminProjectSettlementListItemResponse> findAll() {
        return queryService.findAll().stream()
                .map(AdminProjectSettlementListItemResponse::from)
                .toList();
    }

    @GetMapping("/{settlementId}")
    public AdminProjectSettlementDetailResponse findDetail(@PathVariable Long settlementId) {
        return AdminProjectSettlementDetailResponse.from(queryService.findDetail(settlementId));
    }
}
