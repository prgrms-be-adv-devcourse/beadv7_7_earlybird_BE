package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataCreatorPayoutProfileRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CreatorPayoutProfileRepositoryAdapter implements CreatorPayoutProfileRepository {

    private final SpringDataCreatorPayoutProfileRepository repository;

    @Override
    @Transactional
    public CreatorPayoutProfile save(CreatorPayoutProfile profile) {
        return repository.saveAndFlush(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreatorPayoutProfile> findByCreatorId(Long creatorId) {
        return repository.findById(creatorId);
    }
}
