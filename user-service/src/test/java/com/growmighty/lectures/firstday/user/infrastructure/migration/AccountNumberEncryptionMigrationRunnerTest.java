package com.growmighty.lectures.firstday.user.infrastructure.migration;

import com.growmighty.lectures.firstday.user.config.UserSecurityProperties;
import com.growmighty.lectures.firstday.user.infrastructure.security.UserSensitiveDataCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountNumberEncryptionMigrationRunnerTest {
    private static final String TEST_AES_256_KEY = "MDEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkNERUY=";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private UserSensitiveDataCrypto crypto;
    private AccountNumberEncryptionMigrationRunner runner;

    @BeforeEach
    void setUp() {
        crypto = new UserSensitiveDataCrypto(new UserSecurityProperties(TEST_AES_256_KEY));
        runner = new AccountNumberEncryptionMigrationRunner(jdbcTemplate, crypto);
    }

    // 평문으로 남아있는 기존 행만 골라 암호화하고, 이미 암호화된 행은 건드리지 않는지 검증
    @Test
    void run_encryptsOnlyPlainTextRows() {
        String alreadyEncrypted = crypto.encrypt("220-987-654321");
        when(jdbcTemplate.queryForList("SELECT id, account_number FROM creator_profiles")).thenReturn(List.of(
            Map.of("id", 1L, "account_number", "110-123-456789"),
            Map.of("id", 2L, "account_number", alreadyEncrypted)
        ));

        runner.run();

        verify(jdbcTemplate).update(anyString(), argThatDecryptsTo("110-123-456789"), eq(1L));
        verify(jdbcTemplate, never()).update(anyString(), any(), eq(2L));
    }

    // 마이그레이션할 평문 행이 하나도 없으면 재기동 시 아무 것도 갱신하지 않는지 검증 (idempotent)
    @Test
    void run_withNoPlainTextRows_updatesNothing() {
        when(jdbcTemplate.queryForList("SELECT id, account_number FROM creator_profiles"))
            .thenReturn(List.of(Map.of("id", 1L, "account_number", crypto.encrypt("110-123-456789"))));

        runner.run();

        verify(jdbcTemplate, never()).update(anyString(), any(), any());
    }

    private String argThatDecryptsTo(String expectedPlainText) {
        return org.mockito.ArgumentMatchers.argThat(encrypted -> crypto.decrypt(encrypted).equals(expectedPlainText));
    }
}
