package com.growmighty.lectures.firstday.user.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private final PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

    @Test
    @DisplayName("passwordEncoder 는 BCrypt 구현체다")
    void passwordEncoder_isBCrypt() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("같은 원문 비밀번호도 매번 다른(salted) 해시로 인코딩된다")
    void encode_isSaltedAndNonDeterministic() {
        String rawPassword = "rawPassword1!";

        String encodedOnce = passwordEncoder.encode(rawPassword);
        String encodedTwice = passwordEncoder.encode(rawPassword);

        assertThat(encodedOnce).isNotEqualTo(encodedTwice);
        assertThat(passwordEncoder.matches(rawPassword, encodedOnce)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, encodedTwice)).isTrue();
    }

    @Test
    @DisplayName("틀린 비밀번호는 매칭되지 않는다")
    void matches_withWrongPassword_returnsFalse() {
        String encoded = passwordEncoder.encode("rawPassword1!");

        assertThat(passwordEncoder.matches("wrongPassword!", encoded)).isFalse();
    }
}
