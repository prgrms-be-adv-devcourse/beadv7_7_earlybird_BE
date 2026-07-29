package com.growmighty.lectures.firstday.project.project.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IN_PROGRESS 프로젝트의 fundedAmount를 1분마다 order-service pull 조회로 보정한다.
 * push(주문 확정/취소 시 order-service가 직접 알려주는 방식)는 아직 order-service에 발신 코드가
 * 없어 만들지 않는다 — 이 스케줄러가 push 없이도 fundedAmount를 최신으로 유지하는 유일한 경로다
 * (docs/superpowers/specs/2026-07-28-funded-amount-pull-sync-design.md 참고).
 */
@Component
@RequiredArgsConstructor
public class FundedAmountReconciliationScheduler {

    private final ProjectService projectService;

    @Scheduled(fixedRate = 60 * 1000)
    public void reconcile() {
        projectService.reconcileFundedAmounts();
    }
}
