package com.growmighty.lectures.firstday.settlement.application.payout;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.PAYOUT_PROFILE_NOT_READY;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
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

    @Transactional
    public void registerByAdmin(Long creatorId) {
        CreatorPayoutProfile profile = creatorPayoutProfileRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new SettlementException(PAYOUT_PROFILE_NOT_READY));
        if (profile.status() != CreatorPayoutStatus.REGISTRATION_PENDING) {
            throw new SettlementException(PAYOUT_PROFILE_NOT_READY);
        }
        profile.completeRegistration("dummy-seller-" + creatorId, CreatorPayoutStatus.PAYOUT_READY);
        creatorPayoutProfileRepository.save(profile);
    }
}
