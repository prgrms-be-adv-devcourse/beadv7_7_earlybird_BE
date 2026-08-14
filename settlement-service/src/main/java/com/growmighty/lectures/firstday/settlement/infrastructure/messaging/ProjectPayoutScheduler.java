package com.growmighty.lectures.firstday.settlement.infrastructure.messaging;

import com.growmighty.lectures.firstday.settlement.application.run.ProjectPayoutRunService;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectPayoutScheduler {

    private final ProjectPayoutRunService service;
    private final Clock clock;

    @Scheduled(cron = "${settlement.project-payout.cron:0 0 0 5 * *}", zone = "Asia/Seoul")
    public void runCurrentMonth() {
        service.run(YearMonth.now(clock));
    }
}
