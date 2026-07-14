package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.RewardSnapshot;

/**
 * 후원(주문)이 리워드 도메인에게 요구하는 계약.
 * 후원은 프로젝트가 아니라 리워드(후원 옵션) 단위로 이루어진다.
 */
public interface RewardPort {

    RewardSnapshot getReward(Long rewardId);

    void decreaseStock(Long rewardId, int quantity);

    void restoreStock(Long rewardId, int quantity);
}
