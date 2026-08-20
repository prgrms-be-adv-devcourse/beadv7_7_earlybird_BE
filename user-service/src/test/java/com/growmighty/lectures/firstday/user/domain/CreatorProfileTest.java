package com.growmighty.lectures.firstday.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatorProfileTest {

    @Test
    @DisplayName("userId 가 null 이면 등록할 수 없다")
    void register_withNullUserId_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(null, "88", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지원하지 않는 은행 코드면 등록할 수 없다")
    void register_withUnknownBankCode_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "99", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("은행 코드가 비어 있으면 등록할 수 없다")
    void register_withBlankBankCode_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "", "110-123-456789", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("계좌번호가 비어 있으면 등록할 수 없다")
    void register_withBlankAccountNumber_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "88", "", "창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("예금주명이 비어 있으면 등록할 수 없다")
    void register_withBlankAccountHolder_throws() {
        assertThatThrownBy(() -> CreatorProfile.register(1L, "88", "110-123-456789", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정산 계좌 정보를 등록하면 은행 코드로부터 은행명이 채워지고 필드가 저장된다")
    void register_savesGivenFields() {
        CreatorProfile profile = CreatorProfile.register(1L, "88", "110-123-456789", "창작자");

        assertThat(profile.getUserId()).isEqualTo(1L);
        assertThat(profile.getBankName()).isEqualTo("신한은행");
        assertThat(profile.getBankCode()).isEqualTo("88");
        assertThat(profile.getAccountNumber()).isEqualTo("110-123-456789");
        assertThat(profile.getAccountHolder()).isEqualTo("창작자");
    }

    @Test
    @DisplayName("정산 계좌 정보를 수정하면 새 값으로 대체되고 은행명도 새 코드 기준으로 갱신된다")
    void updateAccount_replacesFields() {
        CreatorProfile profile = CreatorProfile.register(1L, "88", "110-123-456789", "창작자");

        profile.updateAccount("06", "220-987-654321", "새창작자");

        assertThat(profile.getBankName()).isEqualTo("KB국민은행");
        assertThat(profile.getBankCode()).isEqualTo("06");
        assertThat(profile.getAccountNumber()).isEqualTo("220-987-654321");
        assertThat(profile.getAccountHolder()).isEqualTo("새창작자");
    }

    @Test
    @DisplayName("계좌 정보를 수정할 때도 지원하지 않는 은행 코드면 거부된다")
    void updateAccount_withUnknownBankCode_throws() {
        CreatorProfile profile = CreatorProfile.register(1L, "88", "110-123-456789", "창작자");

        assertThatThrownBy(() -> profile.updateAccount("99", "220-987-654321", "새창작자"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
