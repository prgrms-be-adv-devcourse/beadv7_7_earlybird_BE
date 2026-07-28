package com.growmighty.lectures.firstday.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatorProfileTest {

    @Test
    @DisplayName("userId 가 null 이면 등록할 수 없다")
    void register_withNullUserId_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(null, "신한은행", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("은행명이 비어 있으면 등록할 수 없다")
    void register_withBlankBankName_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("계좌번호가 비어 있으면 등록할 수 없다")
    void register_withBlankAccountNumber_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "신한은행", "", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예금주명이 비어 있으면 등록할 수 없다")
    void register_withBlankAccountHolder_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "신한은행", "110-123-456789", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정산 계좌 정보를 등록하면 필드가 그대로 저장된다")
    void register_savesGivenFields() {
        CreatorProfile profile = CreatorProfile.register(1L, "신한은행", "110-123-456789", "창작자");

        assertThat(profile.getUserId()).isEqualTo(1L);
        assertThat(profile.getBankName()).isEqualTo("신한은행");
        assertThat(profile.getAccountNumber()).isEqualTo("110-123-456789");
        assertThat(profile.getAccountHolder()).isEqualTo("창작자");
    }

    @Test
    @DisplayName("정산 계좌 정보를 수정하면 새 값으로 대체된다")
    void updateAccount_replacesFields() {
        CreatorProfile profile = CreatorProfile.register(1L, "신한은행", "110-123-456789", "창작자");

        profile.updateAccount("국민은행", "220-987-654321", "새창작자");

        assertThat(profile.getBankName()).isEqualTo("국민은행");
        assertThat(profile.getAccountNumber()).isEqualTo("220-987-654321");
        assertThat(profile.getAccountHolder()).isEqualTo("새창작자");
    }
}
