package com.growmighty.lectures.firstday.cart.infrastructure.client.dto;

public record ApiResponseBody<T>(boolean success, T data, ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }
}
