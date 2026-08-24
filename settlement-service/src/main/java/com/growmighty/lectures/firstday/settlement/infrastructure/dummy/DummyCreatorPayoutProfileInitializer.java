// TODO(settlement-plan): Seed deterministic creatorId-to-Toss destination fixtures without storing raw account data.
package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "settlement.external-data.mode",
        havingValue = "dummy",
        matchIfMissing = true
)
public class DummyCreatorPayoutProfileInitializer implements ApplicationRunner {

    private static final long DUMMY_CREATOR_ID = 9_000_001L;

    private final CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (creatorPayoutProfileRepository
                .findByCreatorId(DUMMY_CREATOR_ID)
                .isPresent()) {
            return;
        }
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                DUMMY_CREATOR_ID,
                "dummy-seller-9000001",
                CreatorPayoutStatus.PAYOUT_READY
        ));
    }
}
