package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.order.application.port.ProjectFundedAmountPort;
import com.growmighty.lectures.firstday.order.infrastructure.client.dto.FundedAmountUpdateBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "project-service", contextId = "projectFundedAmountFeignClient")
public interface ProjectFundedAmountFeignClient extends ProjectFundedAmountPort {
    @PutMapping("/internal/v1/project/{projectId}/funded-amount")
    void sendFundedAmount(@PathVariable("projectId") Long projectId, @RequestBody FundedAmountUpdateBody body);

    @Override
    default void updateFundedAmount(Long projectId, BigDecimal fundedAmount) {
        sendFundedAmount(projectId, new FundedAmountUpdateBody(fundedAmount));
    }
}
