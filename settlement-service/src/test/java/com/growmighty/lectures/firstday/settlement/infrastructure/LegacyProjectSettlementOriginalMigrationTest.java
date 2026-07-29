package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigration;
import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigrationException;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class LegacyProjectSettlementOriginalMigrationTest {

    @Autowired
    private LegacyProjectSettlementOriginalMigration migration;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("모든 기존 정산의 수수료 원본이 유효할 때만 전체 백필과 필수 제약을 적용한다")
    void backfillsAllLegacyOriginalsOnlyAfterEveryFeePolicyIsValid() {
        ProjectSettlement first = saveSettlement(101L, 201L);
        ProjectSettlement second = saveSettlement(102L, 202L);
        clearOriginals(first.id(), second.id());
        setConflictingFeeRate(second.id());

        assertThatThrownBy(migration::migrate)
                .isInstanceOf(LegacyProjectSettlementOriginalMigrationException.class)
                .hasMessageContaining("settlementId=" + second.id())
                .hasMessageContaining("projectId=102");
        assertThat(countRowsWithoutOriginals()).isEqualTo(2);

        clearFeeRate(second.id());

        int migratedCount = migration.migrate().migratedSettlementCount();
        entityManager.clear();

        assertThat(migratedCount).isEqualTo(2);
        assertThat(projectSettlementRepository.findById(first.id()).orElseThrow())
                .extracting(ProjectSettlement::feePolicySnapshot)
                .isEqualTo(SettlementFeePolicySnapshot.current());
        assertThat(projectSettlementRepository.findById(second.id()).orElseThrow())
                .extracting(ProjectSettlement::feePolicySnapshot)
                .isEqualTo(SettlementFeePolicySnapshot.current());
        assertThat(nullableOriginalColumnCount()).isZero();
        assertThat(legacyProjectTitleColumnIsNullable()).isTrue();
    }

    private ProjectSettlement saveSettlement(Long projectId, Long creatorId) {
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                projectId,
                creatorId,
                SettlementFeePolicySnapshot.current(),
                SettlementBreakdown.of(
                        Money.wons(100_000),
                        Money.wons(4_000),
                        Money.wons(400),
                        Money.wons(4_000),
                        Money.wons(400),
                        Money.wons(0),
                        Money.wons(91_200)
                ),
                PayoutDestinationSnapshot.of(
                        creatorId,
                        "seller-" + creatorId,
                        "088",
                        "********1234"
                ),
                LocalDateTime.of(2026, 7, 1, 9, 0)
        ));
        return settlement;
    }

    private void clearOriginals(Long firstSettlementId, Long secondSettlementId) {
        jdbcTemplate.update(
                """
                        UPDATE project_settlements
                        SET payment_and_settlement_agency_fee_rate = NULL,
                            platform_fee_rate = NULL,
                            fee_vat_rate = NULL
                        WHERE id IN (?, ?)
                        """,
                firstSettlementId,
                secondSettlementId
        );
        entityManager.clear();
    }

    private void setConflictingFeeRate(Long settlementId) {
        jdbcTemplate.update(
                """
                        UPDATE project_settlements
                        SET payment_and_settlement_agency_fee_rate = 0.05
                        WHERE id = ?
                        """,
                settlementId
        );
    }

    private void clearFeeRate(Long settlementId) {
        jdbcTemplate.update(
                """
                        UPDATE project_settlements
                        SET payment_and_settlement_agency_fee_rate = NULL
                        WHERE id = ?
                        """,
                settlementId
        );
    }

    private int countRowsWithoutOriginals() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM project_settlements
                        WHERE payment_and_settlement_agency_fee_rate IS NULL
                           OR platform_fee_rate IS NULL
                           OR fee_vat_rate IS NULL
                        """,
                Integer.class
        );
    }

    private int nullableOriginalColumnCount() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'project_settlements'
                          AND column_name IN (
                              'payment_and_settlement_agency_fee_rate',
                              'platform_fee_rate',
                              'fee_vat_rate'
                          )
                          AND is_nullable = 'YES'
                        """,
                Integer.class
        );
    }

    private boolean legacyProjectTitleColumnIsNullable() {
        Integer nullableColumnCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'project_settlements'
                          AND column_name = 'project_title'
                          AND is_nullable = 'YES'
                        """,
                Integer.class
        );
        return nullableColumnCount != null && nullableColumnCount == 1;
    }
}
