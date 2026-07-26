package com.growmighty.lectures.firstday.settlement.infrastructure.client.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TossPayoutJweCodecTest {

    private static final byte[] SECURITY_KEY = java.util.HexFormat.of().parseHex("01".repeat(32));
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T01:02:03Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final String NONCE = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    @DisplayName("토스 규격의 JWE 헤더로 요청 본문을 암호화한다")
    void encryptsRequestWithRequiredHeader() throws Exception {
        TossPayoutJweCodec codec = new TossPayoutJweCodec(SECURITY_KEY, CLOCK, () -> NONCE);
        String plainText = "[{\"refPayoutId\":\"payout-1\"}]";

        String encrypted = codec.encrypt(plainText);

        JWEObject jweObject = JWEObject.parse(encrypted);
        assertThat(jweObject.getHeader().getAlgorithm()).isEqualTo(JWEAlgorithm.DIR);
        assertThat(jweObject.getHeader().getEncryptionMethod()).isEqualTo(EncryptionMethod.A256GCM);
        assertThat(jweObject.getHeader().getCustomParam("iat"))
                .isEqualTo("2026-07-26T10:02:03+09:00");
        assertThat(jweObject.getHeader().getCustomParam("nonce")).isEqualTo(NONCE);

        jweObject.decrypt(new DirectDecrypter(SECURITY_KEY));
        assertThat(jweObject.getPayload().toString()).isEqualTo(plainText);
    }

    @Test
    @DisplayName("같은 보안 키로 암호화된 성공 또는 실패 응답을 복호화한다")
    void decryptsEncryptedResponse() throws Exception {
        String response = "{\"entityType\":\"payout-list\",\"entityBody\":{\"items\":[]}}";
        JWEObject encryptedResponse = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
                new Payload(response)
        );
        encryptedResponse.encrypt(new DirectEncrypter(SECURITY_KEY));
        TossPayoutJweCodec codec = new TossPayoutJweCodec(SECURITY_KEY, CLOCK, () -> NONCE);

        String decrypted = codec.decrypt(encryptedResponse.serialize());

        assertThat(decrypted).isEqualTo(response);
    }

    @Test
    @DisplayName("각 요청은 서로 다른 nonce를 사용한다")
    void usesUniqueNonceForEachRequest() throws Exception {
        String firstNonce = "123e4567-e89b-12d3-a456-426614174000";
        String secondNonce = "123e4567-e89b-12d3-a456-426614174001";
        java.util.ArrayDeque<String> nonces = new java.util.ArrayDeque<>();
        nonces.add(firstNonce);
        nonces.add(secondNonce);
        TossPayoutJweCodec codec = new TossPayoutJweCodec(SECURITY_KEY, CLOCK, nonces::removeFirst);

        JWEObject first = JWEObject.parse(codec.encrypt("{\"request\":1}"));
        JWEObject second = JWEObject.parse(codec.encrypt("{\"request\":2}"));

        assertThat(first.getHeader().getCustomParam("nonce")).isEqualTo(firstNonce);
        assertThat(second.getHeader().getCustomParam("nonce")).isEqualTo(secondNonce);
    }

    @Test
    @DisplayName("다른 키나 손상된 응답은 복호화하지 않는다")
    void rejectsResponseEncryptedWithAnotherKey() throws Exception {
        byte[] otherKey = java.util.HexFormat.of().parseHex("02".repeat(32));
        JWEObject encryptedResponse = new JWEObject(
                new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
                new Payload("{\"status\":\"REQUESTED\"}")
        );
        encryptedResponse.encrypt(new DirectEncrypter(otherKey));
        TossPayoutJweCodec codec = new TossPayoutJweCodec(SECURITY_KEY, CLOCK, () -> NONCE);

        assertThatThrownBy(() -> codec.decrypt(encryptedResponse.serialize()))
                .isInstanceOf(TossPayoutSecurityException.class)
                .hasMessageContaining("복호화");
    }
}
