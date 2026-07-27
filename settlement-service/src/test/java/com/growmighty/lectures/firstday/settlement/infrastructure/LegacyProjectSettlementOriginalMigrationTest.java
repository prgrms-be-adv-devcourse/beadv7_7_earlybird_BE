package com.growmighty.lectures.firstday.settlement.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigration;
import com.growmighty.lectures.firstday.settlement.application.LegacyProjectSettlementOriginalMigrationException;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTarget;
import com.growmighty.lectures.firstday.settlement.application.port.ProjectSettlementTargetReader;
import com.growmighty.lectures.firstday.settlement.domain.Money;
import com.growmighty.lectures.firstday.settlement.domain.PayoutDestinationSnapshot;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligation;
import com.growmighty.lectures.firstday.settlement.domain.PayoutObligationRepository;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlement;
import com.growmighty.lectures.firstday.settlement.domain.ProjectSettlementRepository;
import com.growmighty.lectures.firstday.settlement.domain.SettlementBreakdown;
import com.growmighty.lectures.firstday.settlement.domain.SettlementFeePolicySnapshot;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@Import(LegacyProjectSettlementOriginalMigrationTest.ProjectTargetTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LegacyProjectSettlementOriginalMigrationTest {

    private static final YearMonth SETTLEMENT_MONTH = YearMonth.of(2026, 7);
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 8, 3);

    @Autowired
    private LegacyProjectSettlementOriginalMigration migration;

    @Autowired
    private ProjectSettlementRepository projectSettlementRepository;

    @Autowired
    private PayoutObligationRepository payoutObligationRepository;

    @Autowired
    private MutableProjectSettlementTargetReader projectTargetReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("모든 기존 정산의 Project 원본을 복원한 뒤에만 전체 백필과 필수 제약을 적용한다")
    void backfillsAllLegacyOriginalsOnlyAfterEveryProjectIsResolved() {
        ProjectSettlement first = saveSettlement(101L, "임시 첫 제목", 201L);
        ProjectSettlement second = saveSettlement(102L, "임시 둘째 제목", 202L);
        clearOriginals(first.id(), second.id());
        projectTargetReader.respondWith(
                SETTLEMENT_MONTH,
                List.of(new ProjectSettlementTarget(101L, "Project 원본 첫 제목", 201L))
        );

        assertThatThrownBy(migration::migrate)
                .isInstanceOf(LegacyProjectSettlementOriginalMigrationException.class)
                .hasMessageContaining("settlementId=" + second.id())
                .hasMessageContaining("projectId=102");
        assertThat(countRowsWithoutOriginals()).isEqualTo(2);

        projectTargetReader.respondWith(
                SETTLEMENT_MONTH,
                List.of(
                        new ProjectSettlementTarget(101L, "Project 원본 첫 제목", 201L),
                        new ProjectSettlementTarget(102L, "Project 원본 둘째 제목", 202L)
                )
        );

        int migratedCount = migration.migrate().migratedSettlementCount();
        entityManager.clear();

        assertThat(migratedCount).isEqualTo(2);
        assertThat(projectSettlementRepository.findById(first.id()).orElseThrow())
                .extracting(ProjectSettlement::projectTitle, ProjectSettlement::feePolicySnapshot)
                .containsExactly("Project 원본 첫 제목", SettlementFeePolicySnapshot.current());
        assertThat(projectSettlementRepository.findById(second.id()).orElseThrow())
                .extracting(ProjectSettlement::projectTitle, ProjectSettlement::feePolicySnapshot)
                .containsExactly("Project 원본 둘째 제목", SettlementFeePolicySnapshot.current());
        assertThat(nullableOriginalColumnCount()).isZero();
    }

    private ProjectSettlement saveSettlement(Long projectId, String projectTitle, Long creatorId) {
        ProjectSettlement settlement = projectSettlementRepository.save(ProjectSettlement.confirm(
                projectId,
                projectTitle,
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
        payoutObligationRepository.save(PayoutObligation.schedule(
                settlement.id(),
                creatorId,
                Money.wons(91_200),
                SCHEDULED_DATE
        ));
        return settlement;
    }

    private void clearOriginals(Long firstSettlementId, Long secondSettlementId) {
        jdbcTemplate.update(
                """
                        UPDATE project_settlements
                        SET project_title = NULL,
                            payment_and_settlement_agency_fee_rate = NULL,
                            platform_fee_rate = NULL,
                            fee_vat_rate = NULL
                        WHERE id IN (?, ?)
                        """,
                firstSettlementId,
                secondSettlementId
        );
        entityManager.clear();
    }

    private int countRowsWithoutOriginals() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM project_settlements
                        WHERE project_title IS NULL
                           OR payment_and_settlement_agency_fee_rate IS NULL
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
                              'project_title',
                              'payment_and_settlement_agency_fee_rate',
                              'platform_fee_rate',
                              'fee_vat_rate'
                          )
                          AND is_nullable = 'YES'
                        """,
                Integer.class
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProjectTargetTestConfig {

        @Bean
        @Primary
        MutableProjectSettlementTargetReader mutableProjectSettlementTargetReader() {
            return new MutableProjectSettlementTargetReader();
        }
    }

    static final class MutableProjectSettlementTargetReader implements ProjectSettlementTargetReader {

        private final Map<YearMonth, List<ProjectSettlementTarget>> targetsByMonth = new ConcurrentHashMap<>();

        void respondWith(YearMonth settlementMonth, List<ProjectSettlementTarget> targets) {
            targetsByMonth.put(settlementMonth, List.copyOf(targets));
        }

        @Override
        public List<ProjectSettlementTarget> findSettlementTargets(YearMonth settlementMonth) {
            return targetsByMonth.getOrDefault(settlementMonth, List.of());
        }
    }
}
