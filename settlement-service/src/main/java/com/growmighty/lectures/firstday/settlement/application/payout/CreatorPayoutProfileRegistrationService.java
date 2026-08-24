package com.growmighty.lectures.firstday.settlement.application.payout;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.CREATOR_INFORMATION_INVALID;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.CREATOR_INFORMATION_UNAVAILABLE;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PAYOUT_PROFILE_NOT_READY;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SELLER_REGISTRATION_REJECTED;
import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SELLER_REGISTRATION_RESULT_UNKNOWN;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGateway;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationGatewayException;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationRequest;
import com.growmighty.lectures.firstday.settlement.application.port.seller.TossSellerRegistrationResult;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformation;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationException;
import com.growmighty.lectures.firstday.settlement.application.port.user.CreatorInformationReader;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorPayoutProfileRegistrationService {

    private final CreatorPayoutProfileRepository creatorPayoutProfileRepository;
    private final CreatorInformationReader creatorInformationReader;
    private final TossSellerRegistrationGateway tossSellerRegistrationGateway;

    @Transactional
    public void registerByAdmin(Long creatorId) {
        CreatorPayoutProfile profile = creatorPayoutProfileRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new SettlementException(PAYOUT_PROFILE_NOT_READY));
        if (profile.status() != CreatorPayoutStatus.REGISTRATION_PENDING) {
            throw new SettlementException(PAYOUT_PROFILE_NOT_READY);
        }
        CreatorInformation creatorInformation = readCreatorInformation(creatorId);
        TossSellerRegistrationResult result = registerSeller(creatorId, creatorInformation);
        if (result instanceof TossSellerRegistrationResult.Rejected) {
            throw new SettlementException(SELLER_REGISTRATION_REJECTED);
        }
        TossSellerRegistrationResult.Registered registered = (TossSellerRegistrationResult.Registered) result;
        profile.completeRegistration(registered.sellerId(), registered.payoutStatus());
        creatorPayoutProfileRepository.save(profile);
    }

    private CreatorInformation readCreatorInformation(Long creatorId) {
        try {
            return creatorInformationReader.read(creatorId);
        } catch (CreatorInformationException exception) {
            throw new SettlementException(
                    exception.failureType() == CreatorInformationException.FailureType.AVAILABILITY
                            ? CREATOR_INFORMATION_UNAVAILABLE
                            : CREATOR_INFORMATION_INVALID,
                    exception
            );
        }
    }

    private TossSellerRegistrationResult registerSeller(Long creatorId, CreatorInformation creatorInformation) {
        try {
            return tossSellerRegistrationGateway.register(new TossSellerRegistrationRequest(creatorId, creatorInformation));
        } catch (TossSellerRegistrationGatewayException exception) {
            throw new SettlementException(SELLER_REGISTRATION_RESULT_UNKNOWN, exception);
        }
    }
}
