package com.growmighty.lectures.firstday.settlement.application.payout;

public interface PayoutExecutor {

    PayoutExecutionResult execute(Long payoutObligationId);
}
