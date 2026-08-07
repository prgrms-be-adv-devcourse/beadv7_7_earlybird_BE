# Reward 재고 변경 API 멱등성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `POST /internal/v1/rewards/{rewardId}/decrease-stock` / `restore-stock`이 같은 논리적 요청을 여러 번 받아도 재고를 한 번만 반영하도록 만든다 (GitHub #195).

**Architecture:** `(orderId, rewardId, operation)` 3개 조합을 새 테이블 `stock_change_logs`에 DB 유니크 제약으로 저장한다. `RewardServiceImpl.decreaseStock`/`restoreStock`은 같은 트랜잭션 안에서 이 로그를 먼저 INSERT하고, 유니크 제약 위반(`DataIntegrityViolationException`)이 나면 재고 변경 없이 조용히 반환한다(200 no-op). 로그 INSERT와 재고 변경이 한 트랜잭션이라 원자적이고, 기존 `@Version`+`@Retryable` 낙관적 락 재시도와도 자연스럽게 맞물린다.

**Tech Stack:** Spring Boot 4.1 / Spring Data JPA / Spring Retry / MySQL(Testcontainers) — project-service 기존 스택 그대로, 신규 의존성 없음.

## Global Constraints

- 이번 스코프는 **project-service만** — order-service가 `orderId`를 실어 보내는 변경은 [GitHub #200](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/200)으로 분리되어 있고 이 플랜의 대상이 아니다.
- `StockChangeRequest.orderId`는 **`@NotNull`** — nullable/하위호환 분기를 만들지 않는다. order-service가 아직 이 필드를 안 보내므로, 이 변경이 배포되면 order-service의 실제 호출은 #200이 먼저(또는 같이) 배포되기 전까지 400을 받는다 — 이는 설계 문서에 이미 명시된 의도된 트레이드오프이며 이 플랜에서 다시 논의하지 않는다.
- 새 테이블 `stock_change_logs`의 보관/정리(retention) 정책은 설계 문서에서 의도적으로 범위 밖으로 남겨졌다 — 이 플랜에도 포함하지 않는다.
- 설계 문서: `docs/superpowers/specs/2026-08-06-reward-stock-idempotency-design.md` (이 플랜의 모든 결정은 이 문서를 따른다. 문서와 플랜이 어긋나면 문서가 우선한다.)

---

## File Structure

| 파일 | 역할 |
| --- | --- |
| `project-service/.../reward/domain/StockChangeOperation.java` (신규) | `DECREASE`/`RESTORE` 구분 enum |
| `project-service/.../reward/domain/StockChangeLog.java` (신규) | 멱등키 3종을 담는 신규 엔티티, `stock_change_logs` 테이블 |
| `project-service/.../reward/infrastructure/StockChangeLogRepository.java` (신규) | `StockChangeLog`용 표준 `JpaRepository` |
| `project-service/.../reward/application/RewardService.java` (수정) | `decreaseStock`/`restoreStock` 시그니처에 `orderId` 추가 |
| `project-service/.../reward/application/RewardServiceImpl.java` (수정) | 멱등성 체크 로직 추가, `StockChangeLogRepository` 의존성 추가 |
| `project-service/.../reward/presentation/dto/request/StockChangeRequest.java` (수정) | `orderId` 필드(`@NotNull`) 추가 |
| `project-service/.../reward/presentation/RewardInternalController.java` (수정) | `request.orderId()` 전달 |
| `project-service/src/test/.../reward/infrastructure/StockChangeLogRepositoryTest.java` (신규) | DB 유니크 제약 자체를 검증 |
| `project-service/src/test/.../reward/application/RewardServiceImplStockChangeIdempotencyTest.java` (신규) | 서비스 레벨 멱등성 로직(mock) 검증 |
| `project-service/src/test/.../reward/presentation/RewardInternalControllerTest.java` (신규) | `orderId` 누락 시 400 검증 (`@WebMvcTest`) |
| `project-service/src/test/.../reward/application/RewardServiceImplRetryTest.java` (수정) | 새 생성자 시그니처에 맞춰 mock bean 추가 (컴파일 유지) |
| `project-service/src/test/.../reward/application/RewardServiceImplOwnershipTest.java` (수정) | 새 생성자 시그니처에 맞춰 mock 추가 (컴파일 유지) |
| `project-service/src/test/.../reward/application/RewardConcurrencyIntegrationTest.java` (수정) | `decreaseStock` 호출에 스레드별 고유 `orderId` 부여 (그렇지 않으면 새 멱등성 로직이 이 테스트의 동시 호출들을 전부 "중복"으로 오인해 기존 검증이 깨진다) |
| `project-service/src/test/.../project/application/ProjectDeleteConcurrencyIntegrationTest.java` (수정) | 위와 동일한 이유로 스레드별 고유 `orderId` 부여 |
| `project-service/src/test/.../reward/application/RewardStockIdempotencyIntegrationTest.java` (신규) | 실제 MySQL/스레드로 동시 중복 요청·decrease+restore 공존을 검증 |

---

### Task 1: `StockChangeLog` 영속성 레이어 (엔티티 + enum + 리포지토리)

**Files:**
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/domain/StockChangeOperation.java`
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/domain/StockChangeLog.java`
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/infrastructure/StockChangeLogRepository.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/infrastructure/StockChangeLogRepositoryTest.java`

**Interfaces:**
- Produces: `StockChangeOperation` enum(`DECREASE`, `RESTORE`), `StockChangeLog.of(Long orderId, Long rewardId, StockChangeOperation operation): StockChangeLog`, `StockChangeLogRepository extends JpaRepository<StockChangeLog, Long>` — Task 2가 이 세 가지를 그대로 사용한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/infrastructure/StockChangeLogRepositoryTest.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.infrastructure;

import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeOperation;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
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
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew :project-service:test --tests "StockChangeLogRepositoryTest"`
Expected: FAIL (컴파일 에러 — `StockChangeLog`, `StockChangeOperation`, `StockChangeLogRepository`가 아직 없음)

- [ ] **Step 3: `StockChangeOperation` enum 작성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/domain/StockChangeOperation.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.domain;

public enum StockChangeOperation {
    DECREASE,
    RESTORE
}
```

- [ ] **Step 4: `StockChangeLog` 엔티티 작성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/domain/StockChangeLog.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.domain;

import com.growmighty.lectures.firstday.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order-service가 보낸 (orderId, rewardId, operation) 조합을 기록해 재고 변경 요청의 중복 도착을
 * 판별한다(#195). 유니크 제약(order_id, reward_id, operation) 위반이 곧 "이미 처리된 요청"이라는
 * 신호다 — 같은 (orderId, rewardId)라도 DECREASE와 RESTORE는 서로 다른 정상 이벤트(주문 시 차감 →
 * 이후 취소 시 복원)라 operation까지 키에 포함한다.
 */
@Entity
@Table(name = "stock_change_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "reward_id", "operation"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockChangeLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "reward_id", nullable = false)
    private Long rewardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockChangeOperation operation;

    private StockChangeLog(Long orderId, Long rewardId, StockChangeOperation operation) {
        this.orderId = orderId;
        this.rewardId = rewardId;
        this.operation = operation;
    }

    public static StockChangeLog of(Long orderId, Long rewardId, StockChangeOperation operation) {
        return new StockChangeLog(orderId, rewardId, operation);
    }
}
```

- [ ] **Step 5: `StockChangeLogRepository` 작성**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/infrastructure/StockChangeLogRepository.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.infrastructure;

import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockChangeLogRepository extends JpaRepository<StockChangeLog, Long> {
}
```

- [ ] **Step 6: 테스트 실행해 통과 확인**

Run: `./gradlew :project-service:test --tests "StockChangeLogRepositoryTest"`
Expected: PASS (2 tests)

- [ ] **Step 7: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/domain/StockChangeOperation.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/domain/StockChangeLog.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/infrastructure/StockChangeLogRepository.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/infrastructure/StockChangeLogRepositoryTest.java
git commit -m "Feat: StockChangeLog 엔티티 및 유니크 제약 추가 (#195)"
```

---

### Task 2: `decreaseStock`/`restoreStock`에 orderId 기반 멱등성 배선

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/application/RewardService.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImpl.java:1-30, 164-219` (import, 생성자 필드, decreaseStock/restoreStock + recover 메서드)
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/presentation/dto/request/StockChangeRequest.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/presentation/RewardInternalController.java:25-35`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplRetryTest.java:46-71` (생성자 시그니처 변경으로 컴파일이 깨지므로 함께 고침)
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplOwnershipTest.java:32-36` (위와 동일한 이유)
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardConcurrencyIntegrationTest.java:50-56, 82-87` (새 멱등성 로직이 이 테스트의 "5개 동시 요청 = 5개의 서로 다른 후원" 가정을 깨뜨리므로 스레드별 고유 orderId 부여)
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectDeleteConcurrencyIntegrationTest.java:59-63` (위와 동일한 이유)
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplStockChangeIdempotencyTest.java` (신규)
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/presentation/RewardInternalControllerTest.java` (신규 — `orderId` 누락 시 400 검증)

**Interfaces:**
- Consumes: Task 1의 `StockChangeOperation`, `StockChangeLog.of(...)`, `StockChangeLogRepository`
- Produces: `RewardService.decreaseStock(Long rewardId, int quantity, Long orderId): void`, `RewardService.restoreStock(Long rewardId, int quantity, Long orderId): void` — Task 3이 이 시그니처로 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성 (서비스 레벨 멱등성 로직, mock)**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplStockChangeIdempotencyTest.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.application.ProjectService;
import com.growmighty.lectures.firstday.project.project.application.ProjectStatusView;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** decreaseStock/restoreStock의 (orderId, rewardId, operation) 멱등성 체크(#195)만 좁게 검증한다. */
class RewardServiceImplStockChangeIdempotencyTest {

    private static final ProjectStatusView PUBLISHED_OPEN_VIEW =
            new ProjectStatusView(true, false, true, "IN_PROGRESS", 1L);

    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final StockChangeLogRepository stockChangeLogRepository = mock(StockChangeLogRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> projectServiceProvider = mock(ObjectProvider.class);
    private final RewardServiceImpl rewardService =
            new RewardServiceImpl(rewardRepository, projectServiceProvider, stockChangeLogRepository);

    private Reward reward;

    @BeforeEach
    void setUp() {
        reward = Reward.register(1L, "노트커버", "설명", BigDecimal.valueOf(10_000), 10);
        when(rewardRepository.findById(anyLong())).thenReturn(Optional.of(reward));
        when(projectServiceProvider.getObject()).thenReturn(projectService);
        when(projectService.findStatusView(anyLong())).thenReturn(Optional.of(PUBLISHED_OPEN_VIEW));
    }

    @Test
    @DisplayName("decreaseStock: 최초 요청이면 재고가 정상 차감된다")
    void decreaseStock_firstRequest_appliesStockChange() {
        rewardService.decreaseStock(1L, 2, 100L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("decreaseStock: 같은 (orderId, rewardId, DECREASE) 재요청이면 재고 변경 없이 조용히 종료된다")
    void decreaseStock_duplicateRequest_noOp() {
        when(stockChangeLogRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        rewardService.decreaseStock(1L, 2, 100L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(10);
        verify(rewardRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("restoreStock: 최초 요청이면 재고가 정상 복원된다")
    void restoreStock_firstRequest_appliesStockChange() {
        reward.decreaseStock(3);

        rewardService.restoreStock(1L, 1, 200L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("restoreStock: 같은 (orderId, rewardId, RESTORE) 재요청이면 재고 변경 없이 조용히 종료된다")
    void restoreStock_duplicateRequest_noOp() {
        reward.decreaseStock(3);
        when(stockChangeLogRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        rewardService.restoreStock(1L, 1, 200L);

        assertThat(reward.getRemainingQuantity()).isEqualTo(7);
        verify(rewardRepository, never()).findById(anyLong());
    }
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew :project-service:test --tests "RewardServiceImplStockChangeIdempotencyTest"`
Expected: FAIL (컴파일 에러 — `RewardServiceImpl` 생성자가 아직 `StockChangeLogRepository`를 받지 않고, `decreaseStock`/`restoreStock`이 아직 3번째 파라미터를 받지 않음)

- [ ] **Step 3: `RewardService` 인터페이스 시그니처 변경**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/application/RewardService.java`에서:

```java
    // ── order-service가 호출하는 내부 API ──────────────────────────
    void decreaseStock(Long rewardId, int quantity);

    void restoreStock(Long rewardId, int quantity);
```

교체:

```java
    // ── order-service가 호출하는 내부 API ──────────────────────────
    /**
     * orderId는 (orderId, rewardId, DECREASE) 멱등키의 일부다(#195) — 같은 조합이 재도착하면
     * 재고를 다시 반영하지 않고 조용히 반환한다.
     */
    void decreaseStock(Long rewardId, int quantity, Long orderId);

    /** decreaseStock과 동일한 멱등성 규칙(#195) — operation이 RESTORE라 DECREASE 로그와 충돌하지 않는다. */
    void restoreStock(Long rewardId, int quantity, Long orderId);
```

- [ ] **Step 4: `RewardServiceImpl`에 `StockChangeLogRepository` 의존성 추가**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImpl.java` 상단 import 블록에 추가:

```java
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeLog;
import com.growmighty.lectures.firstday.project.reward.domain.StockChangeOperation;
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
```

클래스 필드 선언(`private final RewardRepository rewardRepository;` 바로 아래)에 추가:

```java
    private final StockChangeLogRepository stockChangeLogRepository;
```

(`@RequiredArgsConstructor`가 생성자를 자동 생성하므로 필드 추가만으로 생성자 파라미터에 반영된다.)

- [ ] **Step 5: `decreaseStock`/`restoreStock`에 멱등성 체크 추가**

`RewardServiceImpl.java`에서 기존 블록(현재 라인 164~219, `decreaseStock` ~ `recoverRestoreStockConflict`):

```java
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50), recover = "recoverDecreaseStockConflict")
    @Transactional
    public void decreaseStock(Long rewardId, int quantity) {
        Reward reward = getRewardEntity(rewardId);
        findProjectStatus(reward.getProjectId())
            .filter(ProjectStatusView::open)
            .orElseThrow(() -> new IllegalStateException(
                "마감되었거나 진행중이 아닌 프로젝트의 리워드는 주문할 수 없습니다. rewardId=" + rewardId));
        reward.decreaseStock(quantity);
    }

    /**
     * decreaseStock/restoreStock이 파라미터 시그니처(Long, int)가 같아서, operation별로 서로 다른
     * @Recover를 시그니처 자동 매칭에 맡기면 둘 다 먼저 선언된 쪽만 호출되는 문제가 있었다.
     * @Retryable의 recover 속성으로 이름을 직접 지정해 그 모호성을 없앴는데, recover 속성은
     * 메서드 하나만 가리킬 수 있어서 "락 충돌 전용 + catch-all" 2단 구조를 그대로 못 쓴다 — 대신
     * 이 메서드 하나가 RuntimeException을 폭넓게 받고 instanceof로 분기한다. else(원본 재던지기)가
     * 없으면 retryFor에 없는 예외(재고 부족 등)까지 여기로 흘러들어와 매칭 실패로 500이 되므로 필수.
     */
    @Recover
    public void recoverDecreaseStockConflict(RuntimeException e, Long rewardId, int quantity) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "재고 차감 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity);
        }
        throw e;
    }

    /**
     * decreaseStock과 같은 이유로 @Retryable — 동시 취소·환불로 여러 restoreStock 요청이
     * 같은 리워드에 몰리면 낙관적 락 충돌이 날 수 있어, 재시도 없이는 정상적인 동시 복원 요청도
     * 그냥 실패해버린다.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50), recover = "recoverRestoreStockConflict")
    @Transactional
    public void restoreStock(Long rewardId, int quantity) {
        getRewardEntity(rewardId).restoreStock(quantity);
    }

    /** recoverDecreaseStockConflict와 동일한 instanceof 분기 패턴 — restoreStock 전용 메시지. */
    @Recover
    public void recoverRestoreStockConflict(RuntimeException e, Long rewardId, int quantity) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "재고 복원 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity);
        }
        throw e;
    }
```

교체:

```java
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50), recover = "recoverDecreaseStockConflict")
    @Transactional
    public void decreaseStock(Long rewardId, int quantity, Long orderId) {
        if (!tryRegisterStockChange(orderId, rewardId, StockChangeOperation.DECREASE)) {
            return; // 이미 처리된 요청 — 재고를 다시 반영하지 않고 조용히 종료(#195, 200 no-op)
        }
        Reward reward = getRewardEntity(rewardId);
        findProjectStatus(reward.getProjectId())
            .filter(ProjectStatusView::open)
            .orElseThrow(() -> new IllegalStateException(
                "마감되었거나 진행중이 아닌 프로젝트의 리워드는 주문할 수 없습니다. rewardId=" + rewardId));
        reward.decreaseStock(quantity);
    }

    /**
     * decreaseStock/restoreStock이 파라미터 시그니처(Long, int, Long)가 같아서, operation별로 서로
     * 다른 @Recover를 시그니처 자동 매칭에 맡기면 둘 다 먼저 선언된 쪽만 호출되는 문제가 있었다.
     * @Retryable의 recover 속성으로 이름을 직접 지정해 그 모호성을 없앴는데, recover 속성은
     * 메서드 하나만 가리킬 수 있어서 "락 충돌 전용 + catch-all" 2단 구조를 그대로 못 쓴다 — 대신
     * 이 메서드 하나가 RuntimeException을 폭넓게 받고 instanceof로 분기한다. else(원본 재던지기)가
     * 없으면 retryFor에 없는 예외(재고 부족 등)까지 여기로 흘러들어와 매칭 실패로 500이 되므로 필수.
     */
    @Recover
    public void recoverDecreaseStockConflict(RuntimeException e, Long rewardId, int quantity, Long orderId) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "재고 차감 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity
                    + ", orderId=" + orderId);
        }
        throw e;
    }

    /**
     * decreaseStock과 같은 이유로 @Retryable — 동시 취소·환불로 여러 restoreStock 요청이
     * 같은 리워드에 몰리면 낙관적 락 충돌이 날 수 있어, 재시도 없이는 정상적인 동시 복원 요청도
     * 그냥 실패해버린다.
     */
    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50), recover = "recoverRestoreStockConflict")
    @Transactional
    public void restoreStock(Long rewardId, int quantity, Long orderId) {
        if (!tryRegisterStockChange(orderId, rewardId, StockChangeOperation.RESTORE)) {
            return; // 이미 처리된 요청 — 재고를 다시 반영하지 않고 조용히 종료(#195, 200 no-op)
        }
        getRewardEntity(rewardId).restoreStock(quantity);
    }

    /** recoverDecreaseStockConflict와 동일한 instanceof 분기 패턴 — restoreStock 전용 메시지. */
    @Recover
    public void recoverRestoreStockConflict(RuntimeException e, Long rewardId, int quantity, Long orderId) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "재고 복원 중 동시 수정 충돌이 반복되어 실패했습니다. rewardId=" + rewardId + ", quantity=" + quantity
                    + ", orderId=" + orderId);
        }
        throw e;
    }

    /**
     * (orderId, rewardId, operation) 조합을 stock_change_logs에 기록한다 — 유니크 제약 위반이면
     * 이미 처리된 요청이라는 뜻이라 false를 돌려줘 호출자가 재고 변경을 건너뛰게 한다. 같은
     * @Transactional 안에서 호출되므로 이 로그 INSERT와 뒤이은 재고 변경은 원자적으로 커밋/롤백된다
     * — 낙관적 락 충돌로 트랜잭션 전체가 롤백되면 이 로그도 함께 사라지고, @Retryable 재시도마다
     * 로그 INSERT부터 다시 수행된다(#195, 설계 문서의 "선-INSERT 후 예외 포착" 결정).
     */
    private boolean tryRegisterStockChange(Long orderId, Long rewardId, StockChangeOperation operation) {
        try {
            stockChangeLogRepository.save(StockChangeLog.of(orderId, rewardId, operation));
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
```

- [ ] **Step 6: `StockChangeRequest` DTO에 `orderId` 필드 추가**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/presentation/dto/request/StockChangeRequest.java` 전체 교체:

```java
package com.growmighty.lectures.firstday.project.reward.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * POST /internal/rewards/{rewardId}/decrease-stock, /restore-stock
 * orderId는 (orderId, rewardId, operation) 멱등키의 일부다(#195) — 필수값이며, order-service가
 * 이 필드를 실어 보내는 변경은 GitHub #200으로 별도 추적한다.
 */
public record StockChangeRequest(
        @NotNull @Positive Integer quantity,
        @NotNull Long orderId
) {
}
```

- [ ] **Step 7: `RewardInternalController`에서 `orderId` 전달**

`project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/presentation/RewardInternalController.java`에서:

```java
    @PostMapping("/{rewardId}/decrease-stock")
    public Void decreaseStock(@PathVariable Long rewardId, @Valid @RequestBody StockChangeRequest request) {
        rewardService.decreaseStock(rewardId, request.quantity());
        return null;
    }

    @PostMapping("/{rewardId}/restore-stock")
    public Void restoreStock(@PathVariable Long rewardId, @Valid @RequestBody StockChangeRequest request) {
        rewardService.restoreStock(rewardId, request.quantity());
        return null;
    }
```

교체:

```java
    @PostMapping("/{rewardId}/decrease-stock")
    public Void decreaseStock(@PathVariable Long rewardId, @Valid @RequestBody StockChangeRequest request) {
        rewardService.decreaseStock(rewardId, request.quantity(), request.orderId());
        return null;
    }

    @PostMapping("/{rewardId}/restore-stock")
    public Void restoreStock(@PathVariable Long rewardId, @Valid @RequestBody StockChangeRequest request) {
        rewardService.restoreStock(rewardId, request.quantity(), request.orderId());
        return null;
    }
```

- [ ] **Step 8: 새 멱등성 테스트 실행해 통과 확인**

Run: `./gradlew :project-service:test --tests "RewardServiceImplStockChangeIdempotencyTest"`
Expected: PASS (4 tests)

- [ ] **Step 9: 실패하는 컨트롤러 검증 테스트 작성 (`orderId` 누락 시 400)**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/presentation/RewardInternalControllerTest.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.presentation;

import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * orderId는 (orderId, rewardId, operation) 멱등키의 일부라 필수값이다(#195) — 누락 시 서비스까지
 * 가지 않고 400으로 거부되는지 확인한다.
 */
@WebMvcTest(RewardInternalController.class)
class RewardInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RewardService rewardService;

    @Test
    @DisplayName("decrease-stock: orderId가 없으면 400으로 거부되고 서비스는 호출되지 않는다")
    void decreaseStock_missingOrderId_rejectedWith400() throws Exception {
        mockMvc.perform(post("/internal/v1/rewards/{rewardId}/decrease-stock", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rewardService);
    }

    @Test
    @DisplayName("restore-stock: orderId가 없으면 400으로 거부되고 서비스는 호출되지 않는다")
    void restoreStock_missingOrderId_rejectedWith400() throws Exception {
        mockMvc.perform(post("/internal/v1/rewards/{rewardId}/restore-stock", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rewardService);
    }
}
```

- [ ] **Step 10: 테스트 실행해 실패 확인, 이어서 통과 확인**

Run: `./gradlew :project-service:test --tests "RewardInternalControllerTest"`
Expected: Step 6~7을 이미 적용했다면 즉시 PASS(2 tests) — `@NotNull`이 이미 `StockChangeRequest`에 붙어 있으므로. 만약 Step 6을 아직 안 했다면 이 시점에 FAIL(200이 나옴)이어야 정상이다. 이 플랜대로 Step 6~7을 먼저 마쳤다면 바로 PASS를 확인하고 다음 단계로 진행한다.

- [ ] **Step 11: 생성자 시그니처 변경으로 깨진 기존 테스트 고치기 — `RewardServiceImplRetryTest`**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplRetryTest.java`에서 import 블록에 추가:

```java
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
```

`RetryTestConfig` 안 기존:

```java
        @Bean
        RewardService rewardService(RewardRepository rewardRepository, ObjectProvider<ProjectService> projectServiceProvider) {
            return new RewardServiceImpl(rewardRepository, projectServiceProvider);
        }
```

교체:

```java
        @Bean
        StockChangeLogRepository stockChangeLogRepository() {
            return mock(StockChangeLogRepository.class);
        }

        @Bean
        RewardService rewardService(RewardRepository rewardRepository, ObjectProvider<ProjectService> projectServiceProvider,
                                     StockChangeLogRepository stockChangeLogRepository) {
            return new RewardServiceImpl(rewardRepository, projectServiceProvider, stockChangeLogRepository);
        }
```

(이 테스트 파일은 `decreaseStock`/`restoreStock`을 호출하지 않으므로 — `decreaseQuantity`/`update`의 재시도 동작만 검증 — mock의 기본 동작만으로 충분하다.)

- [ ] **Step 12: `RewardServiceImplOwnershipTest` 고치기**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplOwnershipTest.java`에서 import 블록에 추가:

```java
import com.growmighty.lectures.firstday.project.reward.infrastructure.StockChangeLogRepository;
```

기존:

```java
    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> projectServiceProvider = mock(ObjectProvider.class);
    private final RewardServiceImpl rewardService = new RewardServiceImpl(rewardRepository, projectServiceProvider);
```

교체:

```java
    private final RewardRepository rewardRepository = mock(RewardRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final StockChangeLogRepository stockChangeLogRepository = mock(StockChangeLogRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> projectServiceProvider = mock(ObjectProvider.class);
    private final RewardServiceImpl rewardService =
            new RewardServiceImpl(rewardRepository, projectServiceProvider, stockChangeLogRepository);
```

(이 테스트도 `decreaseStock`/`restoreStock`을 호출하지 않으므로 mock 기본 동작으로 충분하다.)

- [ ] **Step 13: `RewardConcurrencyIntegrationTest` 고치기 — 스레드별 고유 orderId 부여**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardConcurrencyIntegrationTest.java`의 첫 번째 테스트 메서드에서 기존:

```java
        for (int i = 0; i < backerThreads; i++) {
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1));
        }
```

교체:

```java
        for (int i = 0; i < backerThreads; i++) {
            long orderId = i; // 스레드마다 다른 후원(주문)이라 orderId도 달라야 한다 — 안 그러면
                               // 새 멱등성 체크가 2번째부터를 전부 "중복 요청"으로 오인해 no-op 처리하고,
                               // 이 테스트가 원래 검증하려는 "동시 요청 각각이 실제로 반영되는지"가 깨진다.
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1, orderId));
        }
```

두 번째 테스트 메서드에서도 동일한 기존 블록을 찾아 같은 방식으로 교체(주석은 반복하지 않아도 된다):

```java
        for (int i = 0; i < backerThreads; i++) {
            long orderId = i;
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1, orderId));
        }
```

- [ ] **Step 14: `ProjectDeleteConcurrencyIntegrationTest` 고치기 — 동일한 이유**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectDeleteConcurrencyIntegrationTest.java`에서 기존:

```java
        for (int i = 0; i < backerThreads; i++) {
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1));
        }
```

교체:

```java
        for (int i = 0; i < backerThreads; i++) {
            long orderId = i; // Task 2/Step 11과 동일한 이유 — 스레드별 고유 orderId 필요
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1, orderId));
        }
```

- [ ] **Step 15: reward 패키지 전체 테스트 실행해 회귀 확인**

Run: `./gradlew :project-service:test --tests "com.growmighty.lectures.firstday.project.reward.*" --tests "com.growmighty.lectures.firstday.project.project.application.ProjectDeleteConcurrencyIntegrationTest"`
Expected: PASS (전부 — 새 테스트, 기존 Reward 테스트, 두 동시성 테스트 모두)

- [ ] **Step 16: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/application/RewardService.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImpl.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/presentation/dto/request/StockChangeRequest.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/reward/presentation/RewardInternalController.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplStockChangeIdempotencyTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/presentation/RewardInternalControllerTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplRetryTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardServiceImplOwnershipTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardConcurrencyIntegrationTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectDeleteConcurrencyIntegrationTest.java
git commit -m "Feat: decreaseStock/restoreStock에 orderId 기반 멱등성 적용 (#195)"
```

---

### Task 3: 동시 중복 요청 / decrease+restore 공존 통합 테스트

**Files:**
- Create: `project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardStockIdempotencyIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2의 `RewardService.decreaseStock(Long, int, Long)` / `restoreStock(Long, int, Long)`, `MySqlIntegrationTestSupport.runAllConcurrently(List<Runnable>)`

- [ ] **Step 1: 테스트 작성**

`project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardStockIdempotencyIntegrationTest.java` (신규 파일):

```java
package com.growmighty.lectures.firstday.project.reward.application;

import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.domain.Reward;
import com.growmighty.lectures.firstday.project.reward.infrastructure.RewardRepository;
import com.growmighty.lectures.firstday.project.support.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진짜 MySQL(Testcontainers)에 진짜 스레드로 동시 중복 요청을 재현해, (orderId, rewardId, operation)
 * 멱등성 체크(#195)가 낙관적 락 재시도와 부딪히지 않고 실제로 "딱 한 번만 반영"을 보장하는지 확인한다.
 */
@SpringBootTest
class RewardStockIdempotencyIntegrationTest extends MySqlIntegrationTestSupport {

    @Autowired
    private RewardService rewardService;
    @Autowired
    private RewardRepository rewardRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("같은 (orderId, rewardId, DECREASE)로 여러 요청이 동시에 와도 재고는 정확히 1번만 차감된다")
    void sameOrderId_concurrentDuplicateDecreaseStock_appliesOnce() throws InterruptedException {
        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);
        long sameOrderId = 999L;

        int duplicateRequests = 5;
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < duplicateRequests; i++) {
            tasks.add(() -> rewardService.decreaseStock(rewardId, 1, sameOrderId));
        }

        Throwable[] results = runAllConcurrently(tasks);

        for (Throwable t : results) {
            assertThat(t).as("멱등성 체크는 예외 없이 조용히 종료돼야 한다").isNull();
        }
        Reward finalReward = rewardRepository.findById(rewardId).orElseThrow();
        assertThat(finalReward.getRemainingQuantity()).isEqualTo(99);
    }

    @Test
    @DisplayName("같은 (orderId, rewardId)에 대해 decrease 후 restore하면 둘 다 반영되어 재고가 원래대로 돌아온다")
    void sameOrderId_decreaseThenRestore_bothApplied() {
        Long projectId = publishedProject();
        Long rewardId = reward(projectId, 100);
        long orderId = 555L;

        rewardService.decreaseStock(rewardId, 3, orderId);
        rewardService.restoreStock(rewardId, 3, orderId);

        Reward finalReward = rewardRepository.findById(rewardId).orElseThrow();
        assertThat(finalReward.getRemainingQuantity()).isEqualTo(100);
    }

    private Long publishedProject() {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project = projectRepository.save(project);
        project.approve();
        project = projectRepository.save(project);
        return project.getProjectId();
    }

    private Long reward(Long projectId, int totalQuantity) {
        Reward reward = Reward.register(projectId, "노트커버", "설명", BigDecimal.valueOf(10_000), totalQuantity);
        return rewardRepository.save(reward).getRewardId();
    }
}
```

- [ ] **Step 2: 테스트 실행해 통과 확인**

Run: `./gradlew :project-service:test --tests "RewardStockIdempotencyIntegrationTest"`
Expected: PASS (2 tests) — Task 2에서 이미 구현된 로직을 동시성/실제 DB 조건에서 재확인하는 테스트라, 이 시점엔 처음부터 통과해야 정상이다. FAIL이 나오면 Task 2의 구현(특히 `tryRegisterStockChange`가 `@Transactional` 경계 안에서 호출되는지)을 다시 확인한다.

- [ ] **Step 3: project-service 전체 테스트 실행해 최종 회귀 확인**

Run: `./gradlew :project-service:test`
Expected: PASS (전체 — MySQL Testcontainers가 필요하므로 Docker가 떠 있어야 한다)

- [ ] **Step 4: 설계 문서 "테스트" 절 체크 반영**

`docs/superpowers/specs/2026-08-06-reward-stock-idempotency-design.md`의 "테스트" 절 항목들이 이 플랜의 Task 1~3으로 전부 커버됐음을 확인한다(별도 문서 수정 불필요 — 이미 계획된 항목과 정확히 일치).

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/test/java/com/growmighty/lectures/firstday/project/reward/application/RewardStockIdempotencyIntegrationTest.java
git commit -m "Test: decreaseStock/restoreStock 멱등성 동시성 통합 테스트 추가 (#195)"
```
