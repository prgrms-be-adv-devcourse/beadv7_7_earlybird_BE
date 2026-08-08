// TODO(settlement-plan): Keep only scenarios needed to exercise requested, completed, failed, and unknown payout outcomes.
package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.infrastructure.dummy.DummyPayoutScenario;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "settlement.dummy-payout")
public final class DummyPayoutProperties {

    private final DummyPayoutScenario scenario;

    public DummyPayoutProperties(DummyPayoutScenario scenario) {
        this.scenario = scenario == null ? DummyPayoutScenario.COMPLETED : scenario;
    }

    public DummyPayoutScenario scenario() {
        return scenario;
    }
}
