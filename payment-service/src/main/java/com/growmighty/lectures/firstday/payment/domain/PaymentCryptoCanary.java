package com.growmighty.lectures.firstday.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_crypto_canaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCryptoCanary {

    public static final long ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 512)
    private String ciphertext;

    private PaymentCryptoCanary(String ciphertext) {
        this.id = ID;
        this.ciphertext = ciphertext;
    }

    // 추가 : 현재 암호화 키를 검증할 기준 암호문을 생성한다.
    public static PaymentCryptoCanary create(String ciphertext) {
        return new PaymentCryptoCanary(ciphertext);
    }
}
