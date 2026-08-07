// TODO(settlement-plan): Keep mapping local and add only the payout-eligible lookup required by monthly execution.
package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.adapter;

import com.growmighty.lectures.firstday.settlement.domain.model.CreatorPayoutProfile;
import com.growmighty.lectures.firstday.settlement.domain.repository.CreatorPayoutProfileRepository;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.entity.CreatorPayoutProfileJpaEntity;
import com.growmighty.lectures.firstday.settlement.infrastructure.persistence.repository.SpringDataCreatorPayoutProfileRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class CreatorPayoutProfileRepositoryAdapter implements CreatorPayoutProfileRepository {

    private final SpringDataCreatorPayoutProfileRepository repository;

    @Override
    @Transactional
    public CreatorPayoutProfile save(CreatorPayoutProfile profile) {
        CreatorPayoutProfileJpaEntity entity;
        if (profile.version() == null) {
            entity = CreatorPayoutProfileJpaEntity.fromDomain(profile);
        } else {
            entity = repository.findById(profile.creatorId())
                    .orElseThrow(() -> new IllegalStateException("저장된 창작자 지급 프로필이 존재하지 않습니다."));
            if (!Objects.equals(entity.version(), profile.version())) {
                throw new ObjectOptimisticLockingFailureException(
                        CreatorPayoutProfile.class,
                        profile.creatorId()
                );
            }
            entity.sync(profile);
        }
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreatorPayoutProfile> findByCreatorId(Long creatorId) {
        return repository.findById(creatorId).map(CreatorPayoutProfileJpaEntity::toDomain);
    }
}
