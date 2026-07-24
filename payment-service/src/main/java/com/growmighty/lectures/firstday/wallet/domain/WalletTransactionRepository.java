package com.growmighty.lectures.firstday.wallet.domain;

import java.util.List;

public interface WalletTransactionRepository {
    WalletTransaction save(WalletTransaction walletTransaction);

    List<WalletTransaction> findByWalletId(Long walletId);
}
