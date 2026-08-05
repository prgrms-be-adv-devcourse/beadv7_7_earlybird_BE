package com.growmighty.lectures.firstday.settlement.infrastructure.dummy;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutStatus;
import java.time.LocalDateTime;
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

    private final CreatorPayoutProfileRepository creatorPayoutProfileRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (creatorPayoutProfileRepository
                .findByCreatorId(DummyProjectSettlementTargetReader.DUMMY_CREATOR_ID)
                .isPresent()) {
            return;
        }
        creatorPayoutProfileRepository.save(CreatorPayoutProfile.registered(
                DummyProjectSettlementTargetReader.DUMMY_CREATOR_ID,
                "dummy-seller-9000001",
                CreatorPayoutStatus.PAYOUT_READY,
                "088",
                "********0001",
                LocalDateTime.of(2026, 7, 23, 0, 0)
        ));
    }
}
