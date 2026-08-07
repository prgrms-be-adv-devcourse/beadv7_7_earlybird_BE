// TODO(settlement-plan): Keep this external seam and align it with the Toss payout request and response objects used by the dummy adapter.
package com.growmighty.lectures.firstday.settlement.application.port.payout;

public interface PayoutGateway {

    PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request);
}
