package com.growmighty.lectures.firstday.settlement.read;

import java.util.List;

/**
 * 정산이 주문 read-model 을 읽기 위한 저장소 계약. 정산은 조회만 한다.
 */
public interface OrderRepository {
    List<Order> findAll();

    /** 페이지 단위 조회 (정산 데모에서 "조금씩 읽기"에 사용) */
    List<Order> findPage(int page, int size);

    long count();
}
