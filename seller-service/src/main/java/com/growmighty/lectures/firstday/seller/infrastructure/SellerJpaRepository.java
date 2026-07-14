package com.growmighty.lectures.firstday.seller.infrastructure;

import com.growmighty.lectures.firstday.seller.domain.Seller;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerJpaRepository extends JpaRepository<Seller, Long> {
    boolean existsByUserId(Long userId);
}
