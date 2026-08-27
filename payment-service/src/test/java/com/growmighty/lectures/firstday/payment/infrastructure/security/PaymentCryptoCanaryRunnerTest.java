package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.domain.PaymentCryptoCanary;
import com.growmighty.lectures.firstday.payment.infrastructure.PaymentCryptoCanaryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCryptoCanaryRunnerTest {

    private static final String CANARY_PLAINTEXT = "payment-crypto-canary-v1";

    @Mock
    private PaymentCryptoCanaryJpaRepository paymentCryptoCanaryJpaRepository;

    @Mock
    private PaymentSensitiveDataCrypto paymentSensitiveDataCrypto;

    private PaymentCryptoCanaryRunner runner;

    @BeforeEach
    void setUp() {
        runner = new PaymentCryptoCanaryRunner(
            paymentCryptoCanaryJpaRepository,
            paymentSensitiveDataCrypto
        );
    }

    @Test
    void bootstrap_현재키로암호화한_canary를저장한다() throws Exception {
        setBootstrap(true);
        when(paymentCryptoCanaryJpaRepository.existsById(PaymentCryptoCanary.ID)).thenReturn(false);
        when(paymentSensitiveDataCrypto.encrypt(CANARY_PLAINTEXT)).thenReturn("encrypted-canary");

        runner.run(null);

        ArgumentCaptor<PaymentCryptoCanary> canaryCaptor = ArgumentCaptor.forClass(PaymentCryptoCanary.class);
        verify(paymentCryptoCanaryJpaRepository).save(canaryCaptor.capture());
        assertThat(canaryCaptor.getValue().getId()).isEqualTo(PaymentCryptoCanary.ID);
        assertThat(canaryCaptor.getValue().getCiphertext()).isEqualTo("encrypted-canary");
    }

    @Test
    void bootstrap_canary가이미있으면실패한다() {
        setBootstrap(true);
        when(paymentCryptoCanaryJpaRepository.existsById(PaymentCryptoCanary.ID)).thenReturn(true);

        assertThatThrownBy(() -> runner.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 존재");

        verifyNoInteractions(paymentSensitiveDataCrypto);
    }

    @Test
    void 일반기동_canary가없으면실패한다() {
        when(paymentCryptoCanaryJpaRepository.findById(PaymentCryptoCanary.ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runner.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("canary가 없어");
    }

    @Test
    void 일반기동_현재키로canary가복호화되면통과한다() throws Exception {
        PaymentCryptoCanary canary = PaymentCryptoCanary.create("encrypted-canary");
        when(paymentCryptoCanaryJpaRepository.findById(PaymentCryptoCanary.ID)).thenReturn(Optional.of(canary));
        when(paymentSensitiveDataCrypto.decrypt("encrypted-canary")).thenReturn(CANARY_PLAINTEXT);

        runner.run(null);

        verify(paymentSensitiveDataCrypto).decrypt("encrypted-canary");
    }

    @Test
    void 일반기동_canary평문이일치하지않으면실패한다() {
        PaymentCryptoCanary canary = PaymentCryptoCanary.create("encrypted-canary");
        when(paymentCryptoCanaryJpaRepository.findById(PaymentCryptoCanary.ID)).thenReturn(Optional.of(canary));
        when(paymentSensitiveDataCrypto.decrypt("encrypted-canary")).thenReturn("different-canary");

        assertThatThrownBy(() -> runner.run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("일치하지 않아");
    }

    private void setBootstrap(boolean bootstrap) {
        ReflectionTestUtils.setField(runner, "bootstrap", bootstrap);
    }
}
