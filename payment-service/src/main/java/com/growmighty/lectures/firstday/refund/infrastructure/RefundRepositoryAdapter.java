package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import com.growmighty.lectures.firstday.refund.domain.RefundStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryAdapter implements RefundRepository {
    private final RefundJpaRepository jpaRepository;

    @Override
    public Refund save(Refund refund) {
        return jpaRepository.save(refund);
    }

    @Override
    public Optional<Refund> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Refund> findByPaymentId(Long paymentId) {
        return jpaRepository.findByPaymentId(paymentId);
    }

    @Override
    public List<Long> findRecoveryTargetIds(LocalDateTime cutoff, int limit) {
        return jpaRepository.findRecoveryTargetIds(RefundStatus.REQUESTED, cutoff, PageRequest.of(0, limit));
    }
}
