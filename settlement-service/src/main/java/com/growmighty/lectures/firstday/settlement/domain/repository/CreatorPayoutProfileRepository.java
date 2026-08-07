// TODO(settlement-plan): Add only the payout-eligibility lookup needed by monthly execution and keep persistence details hidden.
package com.growmighty.lectures.firstday.settlement.domain.repository;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import java.util.Optional;

public interface CreatorPayoutProfileRepository {

    CreatorPayoutProfile save(CreatorPayoutProfile profile);

    Optional<CreatorPayoutProfile> findByCreatorId(Long creatorId);
}
