package com.growmighty.lectures.firstday.project.project.domain;

public enum ProjectStatus {
    PENDING_REVIEW,     // 심사 대기
    REJECTED,           // 심사 반려
    IN_PROGRESS,        // 심사 승인 → 펀딩 진행중
    SUCCEEDED,          // 목표 금액 달성
    FAILED,             // 목표 금액 실패
    CANCELLED;          // 창작자/관리자에 의한 중단

    public boolean isClosed() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
