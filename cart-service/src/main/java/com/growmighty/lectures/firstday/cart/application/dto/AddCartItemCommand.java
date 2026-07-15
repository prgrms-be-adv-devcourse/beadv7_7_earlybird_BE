package com.growmighty.lectures.firstday.cart.application.dto;

public record AddCartItemCommand(Long userId, Long rewardId, int quantity) {
}
