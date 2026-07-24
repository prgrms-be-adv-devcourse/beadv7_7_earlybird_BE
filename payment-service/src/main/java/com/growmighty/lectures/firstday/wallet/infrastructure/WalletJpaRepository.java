package com.growmighty.lectures.firstday.wallet.infrastructure;

import com.growmighty.lectures.firstday.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);
}
