package com.growmighty.lectures.firstday.wallet.infrastructure;

import com.growmighty.lectures.firstday.wallet.domain.Wallet;
import com.growmighty.lectures.firstday.wallet.domain.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryAdapter implements WalletRepository {
    private final WalletJpaRepository jpaRepository;

    @Override
    public Wallet save(Wallet wallet) {
        return jpaRepository.save(wallet);
    }

    @Override
    public Optional<Wallet> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return jpaRepository.findByUserId(userId);
    }
}
