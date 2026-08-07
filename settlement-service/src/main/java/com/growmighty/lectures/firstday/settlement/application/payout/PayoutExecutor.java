// TODO(settlement-plan): Keep payout execution as one deep interface over obligation state; do not expose gateway details to callers.
package com.growmighty.lectures.firstday.settlement.application.payout;

public interface PayoutExecutor {

    PayoutExecutionResult execute(Long payoutObligationId);
}
