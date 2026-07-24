package com.growmighty.lectures.firstday.settlement.domain;

import java.util.Optional;

public interface CreatorPayoutProfileRepository {

    CreatorPayoutProfile save(CreatorPayoutProfile profile);

    Optional<CreatorPayoutProfile> findByCreatorId(Long creatorId);
}
