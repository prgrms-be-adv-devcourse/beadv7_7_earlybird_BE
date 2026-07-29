package com.growmighty.lectures.firstday.settlement.infrastructure.persistence.migration;

import com.growmighty.lectures.firstday.settlement.application.port.LegacyProjectSettlementOriginal;
import com.growmighty.lectures.firstday.settlement.application.port.LegacyProjectSettlementOriginalStore;
import com.growmighty.lectures.firstday.settlement.application.port.ResolvedProjectSettlementOriginal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcLegacyProjectSettlementOriginalStore implements LegacyProjectSettlementOriginalStore {

    private static final String FIND_ALL_ORIGINALS_SQL = """
            SELECT settlement.id AS settlement_id,
                   settlement.project_id,
                   settlement.creator_id,
                   settlement.payment_and_settlement_agency_fee_rate,
                   settlement.platform_fee_rate,
                   settlement.fee_vat_rate
            FROM project_settlements settlement
            ORDER BY settlement.id
            """;

    private static final String BACKFILL_ORIGINAL_SQL = """
            UPDATE project_settlements
            SET payment_and_settlement_agency_fee_rate = COALESCE(
                    payment_and_settlement_agency_fee_rate,
                    ?
                ),
                platform_fee_rate = COALESCE(platform_fee_rate, ?),
                fee_vat_rate = COALESCE(fee_vat_rate, ?)
            WHERE id = ?
              AND project_id = ?
              AND creator_id = ?
            """;

    private static final String COUNT_MISSING_ORIGINALS_SQL = """
            SELECT COUNT(*)
            FROM project_settlements
            WHERE payment_and_settlement_agency_fee_rate IS NULL
               OR platform_fee_rate IS NULL
               OR fee_vat_rate IS NULL
            """;

    private static final String ENFORCE_REQUIRED_ORIGINALS_SQL = """
            ALTER TABLE project_settlements
                MODIFY payment_and_settlement_agency_fee_rate DECIMAL(7, 6) NOT NULL,
                MODIFY platform_fee_rate DECIMAL(7, 6) NOT NULL,
                MODIFY fee_vat_rate DECIMAL(7, 6) NOT NULL
            """;

    private static final String RELAX_LEGACY_PROJECT_TITLE_SQL = """
            ALTER TABLE project_settlements
                MODIFY project_title VARCHAR(255) NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<LegacyProjectSettlementOriginal> findAll() {
        return jdbcTemplate.query(FIND_ALL_ORIGINALS_SQL, (resultSet, rowNumber) ->
                new LegacyProjectSettlementOriginal(
                        resultSet.getLong("settlement_id"),
                        resultSet.getLong("project_id"),
                        resultSet.getLong("creator_id"),
                        resultSet.getBigDecimal("payment_and_settlement_agency_fee_rate"),
                        resultSet.getBigDecimal("platform_fee_rate"),
                        resultSet.getBigDecimal("fee_vat_rate")
                )
        );
    }

    @Override
    @Transactional
    public void backfillAndEnforceRequiredOriginals(List<ResolvedProjectSettlementOriginal> originals) {
        for (ResolvedProjectSettlementOriginal original : originals) {
            int updatedRowCount = jdbcTemplate.update(
                    BACKFILL_ORIGINAL_SQL,
                    original.feePolicySnapshot().paymentAndSettlementAgencyFeeRate(),
                    original.feePolicySnapshot().platformFeeRate(),
                    original.feePolicySnapshot().vatRate(),
                    original.settlementId(),
                    original.projectId(),
                    original.creatorId()
            );
            if (updatedRowCount != 1) {
                throw new IllegalStateException(
                        "프로젝트 정산 원본 백필 대상이 변경되었습니다: settlementId="
                                + original.settlementId()
                                + ", projectId=" + original.projectId()
                );
            }
        }

        Integer missingOriginalCount = jdbcTemplate.queryForObject(
                COUNT_MISSING_ORIGINALS_SQL,
                Integer.class
        );
        if (missingOriginalCount == null || missingOriginalCount > 0) {
            throw new IllegalStateException("필수 프로젝트 정산 원본이 남아 있어 제약을 적용할 수 없습니다.");
        }
        jdbcTemplate.execute(RELAX_LEGACY_PROJECT_TITLE_SQL);
        jdbcTemplate.execute(ENFORCE_REQUIRED_ORIGINALS_SQL);
    }
}
