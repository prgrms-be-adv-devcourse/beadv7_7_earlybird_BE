package com.growmighty.lectures.firstday.project.reward.infrastructure;

import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockChangeLogRepository extends JpaRepository<StockChangeLog, Long> {
}
