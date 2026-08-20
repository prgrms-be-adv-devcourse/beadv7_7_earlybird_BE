package com.growmighty.lectures.firstday.payment.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRecoveryPropertiesTest {

    @Test
    void maximumConfirmingDuration이_confirmationTimeOut보다_짧으면_예외가_발생한다() {
        assertThatThrownBy(() -> new PaymentRecoveryProperties(
            Duration.ofMinutes(3),
            100,
            Duration.ofMinutes(2),
            Duration.ofMinutes(30)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maximumConfirmingDuration은 confirmationTimeOut보다 크거나 같아야 합니다.");
    }

    @Test
    void maximumConfirmingDuration이_confirmationTimeOut과_같거나_크면_생성된다() {
        assertThatCode(() -> new PaymentRecoveryProperties(
            Duration.ofMinutes(3),
            100,
            Duration.ofMinutes(3),
            Duration.ofMinutes(30)
        )).doesNotThrowAnyException();
    }
}
