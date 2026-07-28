package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import java.text.ParseException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class TossPayoutJweCodec {

    private static final int A256_KEY_LENGTH_BYTES = 32;
    private static final DateTimeFormatter ISSUED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    private final byte[] securityKey;
    private final Clock clock;
    private final Supplier<String> nonceSupplier;

    public TossPayoutJweCodec(byte[] securityKey, Clock clock) {
        this(securityKey, clock, () -> UUID.randomUUID().toString());
    }

    TossPayoutJweCodec(
            byte[] securityKey,
            Clock clock,
            Supplier<String> nonceSupplier
    ) {
        if (securityKey == null || securityKey.length != A256_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("A256GCM에는 32바이트 보안 키가 필요합니다.");
        }
        this.securityKey = securityKey.clone();
        this.clock = Objects.requireNonNull(clock, "JWE 발급 시각 Clock은 필수입니다.");
        this.nonceSupplier = Objects.requireNonNull(nonceSupplier, "JWE nonce 공급자는 필수입니다.");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("암호화할 요청 본문은 필수입니다.");
        }

        String nonce = nonceSupplier.get();
        if (nonce == null || nonce.isBlank()) {
            throw new TossPayoutSecurityException("JWE nonce를 생성하지 못했습니다.");
        }

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                .customParam(
                        "iat",
                        OffsetDateTime.now(clock).withNano(0).format(ISSUED_AT_FORMATTER)
                )
                .customParam("nonce", nonce)
                .build();
        JWEObject jweObject = new JWEObject(header, new Payload(plainText));

        try {
            jweObject.encrypt(new DirectEncrypter(securityKey));
            return jweObject.serialize();
        } catch (JOSEException exception) {
            throw new TossPayoutSecurityException("토스 지급대행 요청 본문을 암호화하지 못했습니다.", exception);
        }
    }

    public String decrypt(String compactJwe) {
        if (compactJwe == null || compactJwe.isBlank()) {
            throw new IllegalArgumentException("복호화할 JWE 응답은 필수입니다.");
        }

        try {
            JWEObject jweObject = JWEObject.parse(compactJwe);
            validateAlgorithms(jweObject.getHeader());
            jweObject.decrypt(new DirectDecrypter(securityKey));
            return jweObject.getPayload().toString();
        } catch (ParseException | JOSEException exception) {
            throw new TossPayoutSecurityException("토스 지급대행 응답을 복호화하지 못했습니다.", exception);
        }
    }

    private static void validateAlgorithms(JWEHeader header) {
        if (!JWEAlgorithm.DIR.equals(header.getAlgorithm())
                || !EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())) {
            throw new TossPayoutSecurityException("지원하지 않는 토스 지급대행 JWE 알고리즘입니다.");
        }
    }
}
