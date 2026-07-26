package com.growmighty.lectures.firstday.settlement.application.port;

public interface PayoutGateway {

    PayoutGatewayResult requestScheduledPayout(ScheduledPayoutRequest request);
}
