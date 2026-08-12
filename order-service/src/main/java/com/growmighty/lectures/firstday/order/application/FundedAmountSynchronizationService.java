package com.growmighty.lectures.firstday.order.application;

import com.growmighty.lectures.firstday.order.application.port.ProjectFundedAmountPort;
import com.growmighty.lectures.firstday.order.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FundedAmountSynchronizationService {
    private final OrderRepository orderRepository;
    private final ProjectFundedAmountPort projectFundedAmountPort;

    public void synchronize(Long projectId) {
        BigDecimal fundedAmount = orderRepository.getFundedAmount(projectId)
                .orElse(BigDecimal.ZERO);
        projectFundedAmountPort.updateFundedAmount(projectId, fundedAmount);
    }
}
