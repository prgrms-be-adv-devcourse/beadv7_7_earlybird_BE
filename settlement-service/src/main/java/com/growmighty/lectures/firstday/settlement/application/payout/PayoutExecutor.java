// TODO(settlement-plan): Keep payout execution as one deep interface over settlement payout state.
package com.growmighty.lectures.firstday.settlement.application.payout;

public interface PayoutExecutor {

    PayoutExecutionResult execute(Long settlementId);
}
