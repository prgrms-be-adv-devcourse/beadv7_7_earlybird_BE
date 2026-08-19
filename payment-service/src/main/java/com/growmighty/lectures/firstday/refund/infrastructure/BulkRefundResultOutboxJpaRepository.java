package com.growmighty.lectures.firstday.refund.infrastructure;

import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutbox;
import com.growmighty.lectures.firstday.refund.domain.BulkRefundResultOutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BulkRefundResultOutboxJpaRepository extends JpaRepository<BulkRefundResultOutbox, Long> {

    List<BulkRefundResultOutbox> findByOutboxStatusOrderById(BulkRefundResultOutboxStatus outboxStatus, Pageable pageable);

    @Modifying
    @Query(value = """
      insert into bulk_refund_result_outbox (
          refund_request_id, result_status, outbox_status, retry_count, created_at, updated_at
      ) values (
          :refundRequestId, :resultStatus, 'PENDING', 0, current_timestamp, current_timestamp
      )
      on duplicate key update id = id
      """, nativeQuery = true)
    int insertIfAbsent(
        @Param("refundRequestId") Long refundRequestId,
        @Param("resultStatus") String resultStatus
    );
}
