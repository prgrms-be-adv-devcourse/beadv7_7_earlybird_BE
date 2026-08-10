package com.growmighty.lectures.firstday.settlement.domain.model;

public enum PayoutStatus {
    SCHEDULED,
    PROCESSING,
    RETRY_WAITING,
    COMPLETED,
    ACTION_REQUIRED
}
