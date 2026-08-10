package com.growmighty.lectures.firstday.payment.infrastructure.security;

import com.growmighty.lectures.firstday.payment.config.PaymentSecurityProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class PaymentSensitiveDataCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    //AES-256, GCM (암호화 - 위변조 검증 제공 모드), 결제 키는 노출방지 + DB값 변조도 감지해야함 -> AES/GCM
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    //같은 paymentKey를 여러번 저장해도 같은 암호문이 나오지 않도록 하기 위함 -> 매 암호화 마다 새로운 IV 사용
    private static final int TAG_LENGTH_BITS = 128;
    // GCM 인증 태그의 길이, 복호화 시 암호문 변조 여부 검증 -> 128보다 짧으면 위변조 검증 강도가 낮아짐

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom =  new SecureRandom();
    //암호화 마다 예측 불가능한 IV 생성

    public PaymentSensitiveDataCrypto(PaymentSecurityProperties properties) {
        this.encryptionKey = toAesKey(properties.encryptionKey());
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
            //READY 상태에서는 paymentKey가 없을 수 있음
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                Cipher.ENCRYPT_MODE,
                encryptionKey,
                new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];

            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            // IV는 비밀은 아니지만, 복호화 시 필요하므로 암호문 앞에 함께 저장

            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("결제 민감 정보 암호화에 실패했습니다. " + exception);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }

        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);

            if (encryptedWithIv.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("암호문 형식이 올바르지 않습니다.");
            }

            byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(
                encryptedWithIv,
                IV_LENGTH_BYTES,
                encryptedWithIv.length
            );

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey,
                new GCMParameterSpec(TAG_LENGTH_BITS, iv)
            );

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("결제 민감 정보 복호화에 실패했습니다. " + exception);
        }
    }

    private static final String DEFAULT_DEV_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";

    private SecretKey toAesKey(String encodedKey) {
        String keyToDecode = encodedKey;
        if (keyToDecode == null || keyToDecode.isBlank() || keyToDecode.contains("$")) {
            keyToDecode = DEFAULT_DEV_KEY;
        }
        try {
            byte[] key = Base64.getDecoder().decode(keyToDecode);
            if (key.length != KEY_LENGTH_BYTES) {
                key = Base64.getDecoder().decode(DEFAULT_DEV_KEY);
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException e) {
            byte[] key = Base64.getDecoder().decode(DEFAULT_DEV_KEY);
            return new SecretKeySpec(key, "AES");
        }
    }
}
