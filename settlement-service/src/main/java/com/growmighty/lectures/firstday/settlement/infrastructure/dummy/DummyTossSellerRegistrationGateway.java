package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationResult;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import java.util.Objects;

public final class DummyTossSellerRegistrationGateway implements TossSellerRegistrationGateway {

    public static final String REJECTED_ERROR_CODE = "DUMMY_SELLER_REGISTRATION_REJECTED";

    private final DummyTossSellerRegistrationScenario scenario;

    public DummyTossSellerRegistrationGateway(DummyTossSellerRegistrationScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario, "더미 셀러 등록 시나리오는 필수입니다.");
    }

    @Override
    public TossSellerRegistrationResult register(TossSellerRegistrationRequest request) {
        Objects.requireNonNull(request, "셀러 등록 요청은 필수입니다.");
        return switch (scenario) {
            case PAYOUT_READY -> registered(request.creatorId(), CreatorPayoutStatus.PAYOUT_READY);
            case APPROVAL_REQUIRED -> registered(request.creatorId(), CreatorPayoutStatus.APPROVAL_REQUIRED);
            case KYC_REQUIRED -> registered(request.creatorId(), CreatorPayoutStatus.KYC_REQUIRED);
            case PAYOUT_UNAVAILABLE -> registered(request.creatorId(), CreatorPayoutStatus.PAYOUT_UNAVAILABLE);
            case REJECTED -> new TossSellerRegistrationResult.Rejected(REJECTED_ERROR_CODE);
            case UNKNOWN -> throw new TossSellerRegistrationGatewayException("더미 Toss 셀러 등록 결과가 불명확합니다.");
        };
    }

    private static TossSellerRegistrationResult.Registered registered(Long creatorId, CreatorPayoutStatus status) {
        return new TossSellerRegistrationResult.Registered("dummy-seller-" + creatorId, status);
    }
}
