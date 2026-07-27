package com.growmighty.lectures.firstday.settlement.infrastructure.migration;

import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigration;
import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigrationException;
import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigrationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "settlement.migration.legacy-project-settlement-originals.enabled",
        havingValue = "true"
)
public class LegacyProjectSettlementOriginalMigrationRunner implements ApplicationRunner {

    private final LegacyProjectSettlementOriginalMigration migration;

    @Override
    public void run(ApplicationArguments args) {
        try {
            LegacyProjectSettlementOriginalMigrationResult result = migration.migrate();
            log.info(
                    "기존 프로젝트 정산 원본 백필과 필수 제약 적용 완료: migratedSettlementCount={}",
                    result.migratedSettlementCount()
            );
        } catch (LegacyProjectSettlementOriginalMigrationException exception) {
            log.error("기존 프로젝트 정산 원본 백필 실패: failures={}", exception.failures());
            throw exception;
        }
    }
}
