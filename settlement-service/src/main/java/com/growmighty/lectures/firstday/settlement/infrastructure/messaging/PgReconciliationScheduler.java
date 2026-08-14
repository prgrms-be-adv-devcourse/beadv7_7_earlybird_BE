package com.growmighty.lectures.firstday.settlement.infrastructure.messaging;

import com.growmighty.lectures.firstday.settlement.application.run.PgReconciliationRunService;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PgReconciliationScheduler {

    private final PgReconciliationRunService service;
    private final Clock clock;

    @Scheduled(cron = "${settlement.pg-reconciliation.cron:0 0 0 3 * *}", zone = "Asia/Seoul")
    public void runPreviousMonth() {
        service.run(YearMonth.now(clock).minusMonths(1));
    }
}
