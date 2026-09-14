package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.order.application.port.ProjectFundedAmountPort;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.FundedAmountUpdateBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectFundedAmountHttpClient implements ProjectFundedAmountPort {

    private final ProjectFundedAmountFeignClient projectFundedAmountFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public void updateFundedAmount(Long projectId, BigDecimal fundedAmount) {
        circuitBreakerFactory.create("project").run(
                () -> {
                    projectFundedAmountFeignClient.sendFundedAmount(
                            projectId, new FundedAmountUpdateBody(fundedAmount));
                    return null;
                },
                cause -> fundedAmountSynchronizationFallback(projectId, fundedAmount, cause));
    }

    private Void fundedAmountSynchronizationFallback(Long projectId, BigDecimal fundedAmount, Throwable cause) {
        log.warn("Project fundedAmount synchronization failed. projectId={}, fundedAmount={}, cause={}",
                projectId, fundedAmount, cause.toString());
        return null;
    }
}
