package com.growmighty.lectures.firstday.settlement.application.port.payout;

public interface PayoutGateway {

    PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request);
}
