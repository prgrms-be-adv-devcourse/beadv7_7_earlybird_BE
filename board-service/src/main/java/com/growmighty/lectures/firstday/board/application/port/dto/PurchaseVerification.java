package com.growmighty.lectures.firstday.board.application.port.dto;

/** verified가 false면 rewardName은 의미 없는 값(null)이다 — 호출자는 verified부터 확인해야 한다. */
public record PurchaseVerification(boolean verified, String rewardName) {
}