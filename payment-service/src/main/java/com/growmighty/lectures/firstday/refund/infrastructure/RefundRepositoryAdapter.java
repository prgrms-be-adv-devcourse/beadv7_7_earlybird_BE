package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.Refund;
import com.growmighty.lectures.firstday.refund.domain.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public List<Refund> findByPaymentId(Long paymentId) {
        return jpaRepository.findByPaymentId(paymentId);
    }
}
