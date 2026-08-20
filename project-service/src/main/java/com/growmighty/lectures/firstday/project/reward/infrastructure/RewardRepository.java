package com.growmighty.lectures.firstday.project.reward.infrastructure;

import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RewardRepository extends JpaRepository<Reward, Long> {
    List<Reward> findByProjectId(Long projectId);

    /** 검색 색인 벌크 재구축 시 프로젝트별 N+1 조회를 피하려고 한 번에 묶어 가져온다. */
    List<Reward> findByProjectIdIn(List<Long> projectIds);

    void deleteByProjectId(Long projectId);

    /**
     * 재고를 조건부 원자적 UPDATE로 차감한다 — 엔티티를 읽어 다시 쓰는 낙관적 락 대신, "활성 상태이고
     * 재고가 충분한가"를 WHERE 절에서 DB가 한 번에 검증·반영한다. 반환값 0은 실패(비활성 또는 재고
     * 부족)를 뜻하며, 정확한 원인은 호출부가 실패 후 재조회해 구분한다. version도 함께 증가시켜
     * update()/decreaseQuantity() 같은 엔티티 기반 낙관적 락 경로가 이 변경도 계속 감지하게 한다.
     * totalQuantity(무제한 리워드)가 null인 경우는 remainingQuantity도 null이라 이 쿼리와 맞지 않으므로,
     * 호출부가 그 경우엔 이 메서드를 아예 호출하지 않아야 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Reward r
           SET r.remainingQuantity = r.remainingQuantity - :quantity,
               r.version = r.version + 1
         WHERE r.rewardId = :rewardId
           AND r.active = true
           AND r.remainingQuantity >= :quantity
        """)
    int decreaseStockAtomic(@Param("rewardId") Long rewardId, @Param("quantity") int quantity);

    /**
     * decreaseStockAtomic과 대칭 — 복원 후 총수량을 넘지 않는지 WHERE 절에서 원자적으로 검증한다.
     * totalQuantity가 null인 무제한 리워드는 호출부가 애초에 이 메서드를 호출하지 않아야 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Reward r
           SET r.remainingQuantity = r.remainingQuantity + :quantity,
               r.version = r.version + 1
         WHERE r.rewardId = :rewardId
           AND r.remainingQuantity + :quantity <= r.totalQuantity
        """)
    int restoreStockAtomic(@Param("rewardId") Long rewardId, @Param("quantity") int quantity);
}
