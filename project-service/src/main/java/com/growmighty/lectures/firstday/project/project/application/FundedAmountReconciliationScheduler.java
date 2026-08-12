package com.growmighty.lectures.firstday.project.project.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IN_PROGRESS 프로젝트의 fundedAmount를 order-service push(주문 확정/취소 시 즉시 반영)로
 * 최신 상태를 유지한다. 이 스케줄러는 1시간마다 pull 조회로 재확인하는 백스톱이다 — push가
 * 네트워크 오류 등으로 유실된 경우를 대비한 안전망이며, 평상시 실시간 반영은 push가 담당한다.
 */
@Component
@RequiredArgsConstructor
public class FundedAmountReconciliationScheduler {

    private final ProjectService projectService;

    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void reconcile() {
        projectService.reconcileFundedAmounts();
    }
}
