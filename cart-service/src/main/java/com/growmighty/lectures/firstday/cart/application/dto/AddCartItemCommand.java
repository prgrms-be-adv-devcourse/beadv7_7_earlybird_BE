package com.growmighty.lectures.firstday.cart.application.dto;

public record AddCartItemCommand(Long userId, Long projectId, int quantity) {
}
