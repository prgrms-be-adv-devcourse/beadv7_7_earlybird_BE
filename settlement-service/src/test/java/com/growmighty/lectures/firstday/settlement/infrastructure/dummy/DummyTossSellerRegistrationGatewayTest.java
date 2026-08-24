package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationResult;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import org.junit.jupiter.api.Test;

class DummyTossSellerRegistrationGatewayTest {

    @Test
    void returnsDeterministicSellerIdAndPayoutReadyStatus() {
        DummyTossSellerRegistrationGateway gateway = new DummyTossSellerRegistrationGateway(
                DummyTossSellerRegistrationScenario.PAYOUT_READY
        );

        TossSellerRegistrationResult first = gateway.register(request(7L));
        TossSellerRegistrationResult repeated = gateway.register(request(7L));

        assertThat(first).isEqualTo(repeated);
        assertThat(first).isInstanceOfSatisfying(TossSellerRegistrationResult.Registered.class, registered -> {
            assertThat(registered.sellerId()).isEqualTo("dummy-seller-7");
            assertThat(registered.payoutStatus()).isEqualTo(CreatorPayoutStatus.PAYOUT_READY);
        });
    }

    @Test
    void mapsPayoutEligibilityScenariosToExistingDomainStatuses() {
        assertThat(registeredStatus(DummyTossSellerRegistrationScenario.APPROVAL_REQUIRED))
                .isEqualTo(CreatorPayoutStatus.APPROVAL_REQUIRED);
        assertThat(registeredStatus(DummyTossSellerRegistrationScenario.KYC_REQUIRED))
                .isEqualTo(CreatorPayoutStatus.KYC_REQUIRED);
        assertThat(registeredStatus(DummyTossSellerRegistrationScenario.PAYOUT_UNAVAILABLE))
                .isEqualTo(CreatorPayoutStatus.PAYOUT_UNAVAILABLE);
    }

    @Test
    void distinguishesDefinitiveRejectionFromUnknownResult() {
        DummyTossSellerRegistrationGateway rejected = new DummyTossSellerRegistrationGateway(
                DummyTossSellerRegistrationScenario.REJECTED
        );
        DummyTossSellerRegistrationGateway unknown = new DummyTossSellerRegistrationGateway(
                DummyTossSellerRegistrationScenario.UNKNOWN
        );

        assertThat(rejected.register(request(7L))).isInstanceOfSatisfying(TossSellerRegistrationResult.Rejected.class,
                result -> assertThat(result.errorCode()).isEqualTo(DummyTossSellerRegistrationGateway.REJECTED_ERROR_CODE));
        assertThatThrownBy(() -> unknown.register(request(7L)))
                .isInstanceOf(TossSellerRegistrationGatewayException.class);
    }

    private static CreatorPayoutStatus registeredStatus(DummyTossSellerRegistrationScenario scenario) {
        return ((TossSellerRegistrationResult.Registered) new DummyTossSellerRegistrationGateway(scenario)
                .register(request(7L))).payoutStatus();
    }

    private static TossSellerRegistrationRequest request(Long creatorId) {
        return new TossSellerRegistrationRequest(creatorId, new CreatorInformation(
                "creator@example.com", "창작자", "01012345678"
        ));
    }
}
