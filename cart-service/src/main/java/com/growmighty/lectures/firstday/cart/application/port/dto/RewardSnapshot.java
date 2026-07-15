package com.growmighty.lectures.firstday.cart.application.port.dto;

/**
 * cart 가 필요로 하는 만큼만 담은 리워드 스냅샷(ACL 번역 결과).
 * project 의 응답 전체가 아니라 장바구니 담기 판단에 필요한 값만 노출한다.
 */
public record RewardSnapshot(Long rewardId, boolean orderable) {
}
