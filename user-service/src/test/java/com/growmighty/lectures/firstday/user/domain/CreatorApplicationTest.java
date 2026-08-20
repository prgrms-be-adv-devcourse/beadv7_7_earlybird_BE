package com.growmighty.lectures.firstday.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatorApplicationTest {

    private static CreatorApplication apply() {
        return CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                "123-45-67890", "https://portfolio.example.com",
                "88", "110-123-456789", "창작자");
    }

    @Test
    @DisplayName("userId 가 null 이면 신청할 수 없다")
    void apply_withNullUserId_throws() {
        assertThatThrownBy(() -> CreatorApplication.apply(null, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("창작자명이 비어 있으면 신청할 수 없다")
    void apply_withBlankCreatorName_throws() {
        assertThatThrownBy(() -> CreatorApplication.apply(1L, "", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("은행 코드가 비어 있으면 신청할 수 없다")
    void apply_withBlankBankCode_throws() {
        assertThatThrownBy(() -> CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지원하지 않는 은행 코드면 신청할 수 없다")
    void apply_withUnknownBankCode_throws() {
        assertThatThrownBy(() -> CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "99", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("사업자번호/포트폴리오URL 없이도 신청할 수 있다")
    void apply_withoutOptionalFields_succeeds() {
        CreatorApplication application = CreatorApplication.apply(1L, "창작자", "테크", "소개글",
                null, null, "88", "110-123-456789", "창작자");

        assertThat(application.getBusinessNumber()).isNull();
        assertThat(application.getPortfolioUrl()).isNull();
        assertThat(application.getStatus()).isEqualTo(CreatorApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("신청하면 PENDING 상태로 필드가 그대로 저장된다")
    void apply_savesGivenFieldsAsPending() {
        CreatorApplication application = apply();

        assertThat(application.getUserId()).isEqualTo(1L);
        assertThat(application.getCreatorName()).isEqualTo("창작자");
        assertThat(application.getCategory()).isEqualTo("테크");
        assertThat(application.getIntroduction()).isEqualTo("소개글");
        assertThat(application.getBusinessNumber()).isEqualTo("123-45-67890");
        assertThat(application.getPortfolioUrl()).isEqualTo("https://portfolio.example.com");
        assertThat(application.getBankName()).isEqualTo("신한은행");
        assertThat(application.getBankCode()).isEqualTo("88");
        assertThat(application.getStatus()).isEqualTo(CreatorApplicationStatus.PENDING);
        assertThat(application.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("PENDING 상태를 승인하면 APPROVED로 바뀐다")
    void approve_fromPending_succeeds() {
        CreatorApplication application = apply();

        application.approve();

        assertThat(application.getStatus()).isEqualTo(CreatorApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("이미 승인된 신청을 다시 승인하면 예외가 발생한다")
    void approve_whenAlreadyApproved_throws() {
        CreatorApplication application = apply();
        application.approve();

        assertThatThrownBy(application::approve).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PENDING 상태를 반려하면 REJECTED로 바뀌고 사유가 저장된다")
    void reject_fromPending_succeeds() {
        CreatorApplication application = apply();

        application.reject("서류 미비");

        assertThat(application.getStatus()).isEqualTo(CreatorApplicationStatus.REJECTED);
        assertThat(application.getRejectReason()).isEqualTo("서류 미비");
    }

    @Test
    @DisplayName("반려 사유 없이 반려하면 예외가 발생한다")
    void reject_withBlankReason_throws() {
        CreatorApplication application = apply();

        assertThatThrownBy(() -> application.reject(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이미 승인된 신청을 반려하면 예외가 발생한다")
    void reject_whenAlreadyApproved_throws() {
        CreatorApplication application = apply();
        application.approve();

        assertThatThrownBy(() -> application.reject("사유")).isInstanceOf(IllegalStateException.class);
    }
}
