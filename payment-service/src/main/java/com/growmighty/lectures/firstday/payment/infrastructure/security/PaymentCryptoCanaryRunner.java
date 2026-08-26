package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.domain.PaymentCryptoCanary;
import com.growmighty.lectures.firstday.payment.infrastructure.PaymentCryptoCanaryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCryptoCanaryRunner implements ApplicationRunner {

    private static final String CANARY_PLAINTEXT = "payment-crypto-canary-v1";

    private final PaymentCryptoCanaryJpaRepository paymentCryptoCanaryJpaRepository;
    private final PaymentSensitiveDataCrypto paymentSensitiveDataCrypto;

    @Value("${payment.security.crypto-canary.bootstrap:false}")
    private boolean bootstrap;

    // 추가 : 일반 기동에서는 현재 키를 검증하고, 명시적 bootstrap에서만 기준 암호문을 저장한다.
    @Override
    public void run(ApplicationArguments args) {
        if (bootstrap) {
            bootstrap();
            return;
        }

        verify();
    }

    // 추가 : DB 초기화 직후 현재 키를 기준으로 canary를 한 번 저장한다.
    private void bootstrap() {
        if (paymentCryptoCanaryJpaRepository.existsById(PaymentCryptoCanary.ID)) {
            throw new IllegalStateException("결제 암호화 canary가 이미 존재합니다.");
        }

        paymentCryptoCanaryJpaRepository.save(
            PaymentCryptoCanary.create(paymentSensitiveDataCrypto.encrypt(CANARY_PLAINTEXT))
        );
    }

    // 추가 : 저장된 canary가 현재 키로 복호화되는지 확인한다.
    private void verify() {
        PaymentCryptoCanary canary = paymentCryptoCanaryJpaRepository.findById(PaymentCryptoCanary.ID)
            .orElseThrow(() -> new IllegalStateException("결제 암호화 canary가 없어 기동을 중단합니다."));

        if (!CANARY_PLAINTEXT.equals(paymentSensitiveDataCrypto.decrypt(canary.getCiphertext()))) {
            throw new IllegalStateException("결제 암호화 키가 기존 데이터와 일치하지 않아 기동을 중단합니다.");
        }
    }
}
