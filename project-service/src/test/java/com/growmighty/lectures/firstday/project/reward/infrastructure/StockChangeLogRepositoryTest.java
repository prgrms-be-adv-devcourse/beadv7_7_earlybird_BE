package com.growmighty.lectures.firstday.project.reward.infrastructure;

import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeOperation;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StockChangeLogRepositoryTest extends MySqlIntegrationTestSupport {

    @Autowired
    private StockChangeLogRepository stockChangeLogRepository;

    @Test
    @DisplayName("같은 (orderId, rewardId, operation) 조합을 두 번 저장하면 두 번째는 유니크 제약 위반으로 실패한다")
    void duplicateKey_violatesUniqueConstraint() {
        stockChangeLogRepository.saveAndFlush(StockChangeLog.of(100L, 5L, StockChangeOperation.DECREASE));

        assertThatThrownBy(() ->
                stockChangeLogRepository.saveAndFlush(StockChangeLog.of(100L, 5L, StockChangeOperation.DECREASE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 (orderId, rewardId)라도 operation이 다르면 둘 다 저장된다")
    void sameOrderAndReward_differentOperation_bothSaved() {
        assertThatCode(() -> {
            stockChangeLogRepository.saveAndFlush(StockChangeLog.of(100L, 5L, StockChangeOperation.DECREASE));
            stockChangeLogRepository.saveAndFlush(StockChangeLog.of(100L, 5L, StockChangeOperation.RESTORE));
        }).doesNotThrowAnyException();
    }
}
