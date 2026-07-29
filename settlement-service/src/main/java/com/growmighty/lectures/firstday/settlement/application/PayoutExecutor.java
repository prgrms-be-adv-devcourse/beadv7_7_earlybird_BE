package com.growmighty.lectures.firstday.settlement.application;

public interface PayoutExecutor {

    PayoutExecutionResult execute(Long payoutObligationId);
}
