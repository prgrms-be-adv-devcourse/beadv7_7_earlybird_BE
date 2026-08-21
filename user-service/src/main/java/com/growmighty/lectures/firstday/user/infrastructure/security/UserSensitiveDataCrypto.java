package com.growmighty.lectures.firstday.user.infrastructure.security;

import com.growmighty.lectures.firstday.user.config.UserSecurityProperties;
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
public class UserSensitiveDataCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserSensitiveDataCrypto(UserSecurityProperties properties) {
        this.encryptionKey = toAesKey(properties.encryptionKey());
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
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
            throw new IllegalStateException("사용자 민감 정보 암호화에 실패했습니다. " + exception);
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
            throw new IllegalStateException("사용자 민감 정보 복호화에 실패했습니다. " + exception);
        }
    }

    // 로컬 개발 전용 fallback (프로덕션 값 아님) - USER_SECURITY_ENCRYPTION_KEY 미설정 시 기동을 막지 않기 위함
    private static final String DEFAULT_DEV_KEY = "sMbktDhYJlsP/gZYemDE1+qcwwMtQ8a0jpj0+THtaBg=";

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
