package com.growmighty.lectures.firstday.order.infrastructure.client;

import com.growmighty.lectures.firstday.order.infrastructure.client.dto.FundedAmountUpdateBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "project-service", contextId = "projectFundedAmountFeignClient")
public interface ProjectFundedAmountFeignClient {
    @PutMapping("/internal/v1/projects/{projectId}/funded-amount")
    void sendFundedAmount(@PathVariable("projectId") Long projectId, @RequestBody FundedAmountUpdateBody body);
}
