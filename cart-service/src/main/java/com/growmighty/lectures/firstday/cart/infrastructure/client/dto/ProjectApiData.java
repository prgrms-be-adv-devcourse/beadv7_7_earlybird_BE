package com.growmighty.lectures.firstday.cart.infrastructure.client.dto;

public record ProjectApiData(
        Long id,
        String title,
        String status,
        boolean orderable
) {
}
