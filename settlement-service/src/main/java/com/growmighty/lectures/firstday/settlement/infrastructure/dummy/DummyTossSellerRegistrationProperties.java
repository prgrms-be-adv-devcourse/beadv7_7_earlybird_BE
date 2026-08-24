package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.dummy-toss-seller-registration")
public final class DummyTossSellerRegistrationProperties {

    private final DummyTossSellerRegistrationScenario scenario;

    public DummyTossSellerRegistrationProperties(DummyTossSellerRegistrationScenario scenario) {
        this.scenario = scenario == null ? DummyTossSellerRegistrationScenario.PAYOUT_READY : scenario;
    }

    public DummyTossSellerRegistrationScenario scenario() {
        return scenario;
    }
}
