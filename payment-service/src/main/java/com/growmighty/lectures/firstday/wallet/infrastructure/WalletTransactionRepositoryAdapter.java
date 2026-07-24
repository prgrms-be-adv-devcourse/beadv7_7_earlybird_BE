package com.growmighty.lectures.firstday.wallet.infrastructure;

import com.growmighty.lectures.firstday.wallet.domain.WalletTransaction;
import com.growmighty.lectures.firstday.wallet.domain.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryAdapter implements WalletTransactionRepository {
    private final WalletTransactionJpaRepository jpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction walletTransaction) {
        return jpaRepository.save(walletTransaction);
    }

    @Override
    public List<WalletTransaction> findByWalletId(Long walletId) {
        return jpaRepository.findByWalletId(walletId);
    }
}
