package com.growmighty.lectures.firstday.payment.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(
    String code,
    String message
) {
}
