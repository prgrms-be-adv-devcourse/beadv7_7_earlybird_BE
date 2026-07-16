package com.growmighty.lectures.firstday.wallet.infrastructure;

import com.growmighty.lectures.firstday.wallet.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWalletId(Long walletId);
}
