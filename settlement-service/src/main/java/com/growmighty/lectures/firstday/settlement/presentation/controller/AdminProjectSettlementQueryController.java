// TODO(settlement-plan): Expose new reconciliation and payout states only through the existing admin query interface.
package com.growmighty.lectures.firstday.settlement.presentation.controller;

import com.growmighty.lectures.firstday.settlement.application.query.AdminProjectSettlementQueryService;
import com.growmighty.lectures.firstday.settlement.application.query.AdminSettlementSort;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.AdminProjectSettlementDetailResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.AdminProjectSettlementListItemResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.AdminProjectReconciliationReviewDetailResponse;
import com.growmighty.lectures.firstday.settlement.presentation.dto.response.AdminProjectRefundDetailResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements/all")
@RequiredArgsConstructor
public class AdminProjectSettlementQueryController {

    private final AdminProjectSettlementQueryService queryService;

    @GetMapping
    public List<AdminProjectSettlementListItemResponse> findAll(
            @RequestParam(defaultValue = "PUBLISHED_AT") AdminSettlementSort sort
    ) {
        return queryService.findAll(sort).stream()
                .map(AdminProjectSettlementListItemResponse::from)
                .toList();
    }

    @GetMapping("/{settlementId}")
    public AdminProjectSettlementDetailResponse findDetail(@PathVariable Long settlementId) {
        return AdminProjectSettlementDetailResponse.from(queryService.findDetail(settlementId));
    }

    @GetMapping("/refunds/{refundRequestId}")
    public AdminProjectRefundDetailResponse findRefundDetail(@PathVariable Long refundRequestId) {
        return AdminProjectRefundDetailResponse.from(queryService.findRefundDetail(refundRequestId));
    }

    @GetMapping("/projects/{projectId}/reconciliation-review")
    public AdminProjectReconciliationReviewDetailResponse findReconciliationReviewDetail(@PathVariable Long projectId) {
        return AdminProjectReconciliationReviewDetailResponse.from(queryService.findReconciliationReviewDetail(projectId));
    }
}
