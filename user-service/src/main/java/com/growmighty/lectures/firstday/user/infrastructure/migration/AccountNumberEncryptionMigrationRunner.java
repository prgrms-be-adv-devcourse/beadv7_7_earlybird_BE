package com.growmighty.lectures.firstday.user.infrastructure.migration;

import com.growmighty.lectures.firstday.user.infrastructure.security.UserSensitiveDataCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * accountNumber 평문 저장 시절(#123 이전)에 쌓인 기존 행을 AES-GCM 암호문으로 옮긴다.
 * JPA 엔티티/컨버터를 거치지 않고 원시 SQL로 직접 읽고 쓴다 — 컨버터를 거치면 아직 평문인
 * 행에서 즉시 복호화 실패가 나기 때문이다. decrypt 성공 여부로 이미 암호화된 행을 가려내(GCM
 * 인증 태그 덕에 오탐 사실상 불가능) 재기동마다 반복 실행해도 안전하다(idempotent) —
 * UserDataInitializer와 같은 방식으로 매 기동 시 실행되며, 남은 평문 행이 없으면 그대로 no-op.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
public class AccountNumberEncryptionMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final UserSensitiveDataCrypto crypto;

    @Override
    public void run(String... args) {
        var rows = jdbcTemplate.queryForList("SELECT id, account_number FROM creator_profiles");

        int migrated = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String accountNumber = (String) row.get("account_number");

            if (isAlreadyEncrypted(accountNumber)) {
                continue;
            }

            String encrypted = crypto.encrypt(accountNumber);
            jdbcTemplate.update("UPDATE creator_profiles SET account_number = ? WHERE id = ?", encrypted, id);
            migrated++;
        }

        if (migrated > 0) {
            System.out.printf("[migration] accountNumber 암호화 완료. %d건 마이그레이션 (#123)%n", migrated);
        }
    }

    private boolean isAlreadyEncrypted(String accountNumber) {
        try {
            crypto.decrypt(accountNumber);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
