package com.growmighty.lectures.firstday.settlement.domain;

import java.util.Optional;

public interface SettlementRepository {
    Settlement save(Settlement settlement);

    Optional<Settlement> findByOrderId(Long orderId);

    /** 멱등성 체크: 이미 정산된 주문인지 확인 (재시작 시 중복 정산 방지) */
    boolean existsByOrderId(Long orderId);

    long count();

    void deleteAll();
}
