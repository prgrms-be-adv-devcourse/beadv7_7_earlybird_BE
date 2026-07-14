package com.growmighty.lectures.firstday.project.presentation.dto;

import lombok.NonNull;

import java.math.BigDecimal;

public record ChangeProjectPriceRequest(@NonNull BigDecimal price) {
}
