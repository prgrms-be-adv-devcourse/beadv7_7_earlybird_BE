# 프로젝트 모금액(fundedAmount) 주기적 pull 동기화 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** project-service가 order-service를 1분마다 pull 조회해 `Project.fundedAmount`를 실제 결제 확정(PAID) 누적 총액과 일치시켜, 목표 달성 판정(`closeByDeadline`)과 조기 종료(`closeEarlyAsSucceeded`)가 정상 동작하게 한다.

**Architecture:** `ProjectServiceImpl`에 이미 주입된 `OrderPort`에 `getFundedAmount(Long projectId)`를 추가하고, `Project` 도메인에 절대값 덮어쓰기용 `updateFundedAmount(BigDecimal)`를 추가한다. 그 위에 `ProjectDeadlineScheduler`와 동일한 패턴의 `FundedAmountReconciliationScheduler`(1분 주기)를 신설해, `IN_PROGRESS` 프로젝트마다 order-service를 조회하고 갱신한다. push(PUT 수신)는 만들지 않는다 — order-service에 발신 코드가 없어 지금 만들면 호출자 없는 데드코드가 되기 때문이다 (`docs/superpowers/specs/2026-07-28-funded-amount-pull-sync-design.md` 참고).

**Tech Stack:** Spring Boot(WebMVC), Spring Cloud OpenFeign, Resilience4j CircuitBreaker, Spring Retry(`@Retryable`/`@Recover`), Spring `@Scheduled`, JUnit5 + Mockito + AssertJ, `@SpringJUnitConfig`(재시도 검증용).

## Global Constraints

- 이 스펙/계획의 작업 범위는 **project-service만** — order-service/다른 서비스 코드는 건드리지 않는다.
- push(PUT 수신 엔드포인트, `FundedAmountUpdateRequest`)는 이번 범위에 포함하지 않는다 — order-service가 발신 코드를 만들기 전까지는 데드코드다.
- 폴링 주기는 **1분**(`fixedRate = 60 * 1000`), `ProjectDeadlineScheduler`와 같은 위치·같은 패턴을 따른다.
- 모든 신규 브랜치는 로컬 worktree가 아니라 **최신 `origin/develop`**을 기준으로 만든다 — 현재 워크트리 브랜치(`강대혁/project/funding-period-cap`)는 이미 PR #110으로 develop에 머지됐고, `OrderPort`/`OrderFeignClient`/`OrderHttpClient`/`RewardService` 인터페이스 등 develop에 이미 반영된 구조(order-existence-dip, refactor-dip)가 로컬엔 없어 그대로 쓰면 안 된다. 아래 모든 코드 스니펫은 **`origin/develop` 기준 현재 파일 내용**이다.
- 낙관적 락 재시도는 기존 관례(`@Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))` + 전용 `@Recover`)를 그대로 따른다.
- fail-closed 원칙: order-service 호출 실패는 값을 0 등으로 잘못 덮어쓰지 말고 `ServiceUnavailableException`(503)으로 알린 뒤 그 프로젝트만 건너뛴다.

---

### Task 1: 브랜치 준비

**Files:** 없음 (git 작업만)

- [ ] **Step 1: 최신 develop을 받고 새 브랜치를 만든다**

```bash
git fetch origin
git checkout -b 강대혁/project/funded-amount-pull-sync origin/develop
```

- [ ] **Step 2: 베이스가 예상한 상태인지 확인한다**

Run: `git show HEAD:project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/OrderPort.java`
Expected: `hasOrderedReward(Long projectId)` 하나만 있고 `getFundedAmount`는 아직 없음 (이 계획에서 추가할 대상).

---

### Task 2: `Project.updateFundedAmount()` 도메인 메서드

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java`

**Interfaces:**
- Produces: `Project.updateFundedAmount(BigDecimal fundedAmount)` — 절대값 덮어쓰기, `null` 또는 음수면 `IllegalArgumentException`. 이후 Task 4에서 `ProjectServiceImpl`이 이 메서드를 호출한다.

- [ ] **Step 1: 실패하는 테스트 3개를 `ProjectTest.java`에 추가한다**

`마감일 연장도 시작일로부터 3개월을 초과할 수 없다` 테스트(`extendDeadline_beyondThreeMonthsFromStartAt_throws`) 바로 다음, `배치 마감 처리는 진행중 상태에서만 가능하다` 테스트 앞에 삽입한다:

```java
    @Test
    @DisplayName("모금액을 0 이상의 값으로 갱신할 수 있다")
    void updateFundedAmount_valid_updates() {
        Project project = project();
        project.updateFundedAmount(BigDecimal.valueOf(500_000));
        assertThat(project.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(500_000));
    }

    @Test
    @DisplayName("모금액을 음수로 갱신할 수 없다")
    void updateFundedAmount_negative_throws() {
        Project project = project();
        assertThatThrownBy(() -> project.updateFundedAmount(BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("모금액을 null로 갱신할 수 없다")
    void updateFundedAmount_null_throws() {
        Project project = project();
        assertThatThrownBy(() -> project.updateFundedAmount(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

```

- [ ] **Step 2: 컴파일 실패(메서드 없음)를 확인한다**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `cannot find symbol: method updateFundedAmount`

- [ ] **Step 3: `Project.java`에 메서드를 추가한다**

먼저 `fundedAmount` 필드의 낡은 TODO 주석을 교체한다:

```java
    // TODO(팀): 결제 성공 시 이 값을 실제로 채워주는 트리거가 아직 없다(order/payment-service
    // 확인 결과 프로젝트 단위 누적 모금액 집계·통보 API 없음, 주문 1건당 totalAmount만 존재).
    // order/payment-service와 "언제·어떻게 알려줄지" 별도로 맞춰야 하는 cross-service 이슈.
    @Column(nullable = false)
    private BigDecimal fundedAmount;
```
→
```java
    // updateFundedAmount()로 갱신된다 — project-service가 1분마다 order-service를 pull 조회해
    // 이 값을 확정 누적 총액으로 덮어쓴다(FundedAmountReconciliationScheduler 참고).
    @Column(nullable = false)
    private BigDecimal fundedAmount;
```

`extendDeadline(LocalDate newEndAt)` 메서드 바로 다음에 새 메서드를 추가한다:

```java
    /**
     * order-service에서 pull 조회해온 "현재 확정 누적 총액"으로 덮어쓴다 — 증분이 아니라 절대값이라
     * 같은 값으로 여러 번 호출해도 결과가 같다(멱등).
     */
    public void updateFundedAmount(BigDecimal fundedAmount) {
        if (fundedAmount == null || fundedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("모금액은 0 이상이어야 합니다. fundedAmount=" + fundedAmount);
        }
        this.fundedAmount = fundedAmount;
    }
```

`closeByDeadline()` 자바독에서 이제 사실이 아닌 문장을 지운다:

```java
    /**
     * 배치 전용: 마감시각이 지난 진행중 프로젝트를 모금액 기준으로 성공/실패 확정한다.
     * fundedAmount가 아직 항상 0인 이유는 필드 선언부 TODO 참고.
     */
    public void closeByDeadline() {
```
→
```java
    /**
     * 배치 전용: 마감시각이 지난 진행중 프로젝트를 모금액 기준으로 성공/실패 확정한다.
     */
    public void closeByDeadline() {
```

- [ ] **Step 4: 테스트 통과를 확인한다**

Run: `./gradlew :project-service:test --tests "ProjectTest"`
Expected: PASS (전부, 기존 테스트 포함)

- [ ] **Step 5: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/domain/Project.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/domain/ProjectTest.java
git commit -m "Feat: Project.updateFundedAmount() 도메인 메서드 추가"
```

---

### Task 3: `OrderPort`/`OrderFeignClient`/`OrderHttpClient`에 `getFundedAmount` 추가

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/OrderPort.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/client/OrderFeignClient.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/client/OrderHttpClient.java`
- Test: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/client/OrderHttpClientTest.java`

**Interfaces:**
- Produces: `OrderPort.getFundedAmount(Long projectId): BigDecimal` — 항상 0 이상의 값을 반환(무후원 프로젝트는 0), order-service 장애 시 `ServiceUnavailableException` 던짐(fail-closed). Task 4에서 `ProjectServiceImpl`이 이 포트를 호출한다.
- Consumes: order-service의 기존 `GET /internal/v1/orders/{projectId}/funded-amount` (PR #81, 이미 `develop`에 있음) — 응답 바디가 없으면(무후원) `data`가 `null`.

- [ ] **Step 1: 실패하는 테스트 3개를 `OrderHttpClientTest.java`에 추가한다**

기존 `hasOrderedReward_failure_throwsServiceUnavailable` 테스트 다음(클래스 마지막 `}` 앞)에 추가:

```java

    @Test
    @DisplayName("모금액 조회가 성공하면 응답의 data를 그대로 반환한다")
    void getFundedAmount_success() {
        when(orderFeignClient.getFundedAmount(1L))
                .thenReturn(ApiResponse.ok(BigDecimal.valueOf(500_000)));

        BigDecimal result = orderHttpClient.getFundedAmount(1L);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(500_000));
    }

    @Test
    @DisplayName("아직 후원이 없는 프로젝트는 order-service가 data=null을 주고, 0원으로 처리한다")
    void getFundedAmount_noOrders_returnsZero() {
        when(orderFeignClient.getFundedAmount(1L))
                .thenReturn(ApiResponse.ok(null));

        BigDecimal result = orderHttpClient.getFundedAmount(1L);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("모금액 조회 호출이 실패하면 조용히 넘기지 않고 503으로 변환한다 (fail-closed)")
    void getFundedAmount_failure_throwsServiceUnavailable() {
        when(orderFeignClient.getFundedAmount(1L)).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> orderHttpClient.getFundedAmount(1L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
```

파일 상단 import에 `java.math.BigDecimal`을 추가한다:

```java
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
```
→
```java
import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.common.response.ApiResponse;

import java.math.BigDecimal;
```

- [ ] **Step 2: 컴파일 실패를 확인한다**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `cannot find symbol: method getFundedAmount` (`OrderFeignClient`/`OrderHttpClient`에 아직 없음)

- [ ] **Step 3: `OrderPort.java`에 메서드를 추가한다**

```java
package com.growmighty.lectures.firstday.project.project.application.port;

import java.math.BigDecimal;

/**
 * 프로젝트 삭제 시 order-service에게 "이 프로젝트에 후원(주문) 이력이 있는지" 묻는 계약,
 * 그리고 1분마다 "지금 확정 누적 총액이 얼마인지" pull 조회하는 계약.
 */
public interface OrderPort {

    boolean hasOrderedReward(Long projectId);

    /** 무후원 프로젝트는 0을 반환한다(음수 없음). order-service 장애 시 예외를 던진다(fail-closed). */
    BigDecimal getFundedAmount(Long projectId);
}
```

- [ ] **Step 4: `OrderFeignClient.java`에 메서드를 추가한다**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "order-service")
public interface OrderFeignClient {

    @GetMapping("/internal/v1/orders/{projectId}/ordered-existence")
    ApiResponse<Boolean> hasOrderedReward(@PathVariable("projectId") Long projectId);

    @GetMapping("/internal/v1/orders/{projectId}/funded-amount")
    ApiResponse<BigDecimal> getFundedAmount(@PathVariable("projectId") Long projectId);
}
```

- [ ] **Step 5: `OrderHttpClient.java`에 구현을 추가한다**

```java
package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderHttpClient implements OrderPort {

    private final OrderFeignClient orderFeignClient;
    private final CircuitBreakerFactory circuitBreakerFactory;

    @Override
    public boolean hasOrderedReward(Long projectId) {
        return circuitBreakerFactory.create("order").run(
            () -> orderFeignClient.hasOrderedReward(projectId).data(),
            this::hasOrderedRewardFallback);
    }

    private boolean hasOrderedRewardFallback(Throwable cause) {
        log.warn("주문 존재 여부 확인 호출 실패 → fallback 실행. 원인: {}", cause.toString());
        // "확인 안 됨"을 "후원 없음"으로 잘못 판단해 삭제를 허용하면 안 된다 —
        // 결제와 같은 이유로, 실패는 정직하게 503으로 알리고 삭제는 막는다(fail-closed).
        throw new ServiceUnavailableException(
            "주문 서비스가 일시적으로 응답하지 않아 삭제 가능 여부를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Override
    public BigDecimal getFundedAmount(Long projectId) {
        return circuitBreakerFactory.create("order").run(
            () -> resolveFundedAmount(orderFeignClient.getFundedAmount(projectId).data()),
            this::getFundedAmountFallback);
    }

    /** 아직 후원이 없는 프로젝트는 order-service가 data=null을 준다 — 0원으로 취급한다. */
    private BigDecimal resolveFundedAmount(BigDecimal fundedAmount) {
        return fundedAmount != null ? fundedAmount : BigDecimal.ZERO;
    }

    private BigDecimal getFundedAmountFallback(Throwable cause) {
        log.warn("모금액 확정 총액 조회 실패 → fallback 실행. 원인: {}", cause.toString());
        // 이 호출은 project.fundedAmount를 덮어쓸 값을 가져오는 pull이라, 실패했다고 0 등으로
        // 잘못 덮어쓰면 안 된다 — 호출자(ProjectServiceImpl.reconcileFundedAmounts)가 이번
        // 프로젝트만 건너뛰도록 정직하게 503으로 알린다.
        throw new ServiceUnavailableException(
            "주문 서비스가 일시적으로 응답하지 않아 모금액을 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
}
```

- [ ] **Step 6: 테스트 통과를 확인한다**

Run: `./gradlew :project-service:test --tests "OrderHttpClientTest"`
Expected: PASS (5개: 기존 `hasOrderedReward` 2개 + 신규 `getFundedAmount` 3개)

- [ ] **Step 7: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/port/OrderPort.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/client/OrderFeignClient.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/infrastructure/client/OrderHttpClient.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/infrastructure/client/OrderHttpClientTest.java
git commit -m "Feat: OrderPort에 모금액 pull 조회(getFundedAmount) 추가"
```

---

### Task 4: `ProjectService`/`ProjectServiceImpl` — `updateFundedAmount` + `reconcileFundedAmounts`

**Files:**
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java`
- Modify: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java`
- Modify: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplRetryTest.java`
- Create: `project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReconciliationTest.java`

**Interfaces:**
- Consumes: `Project.updateFundedAmount(BigDecimal)`(Task 2), `OrderPort.getFundedAmount(Long)`(Task 3), 기존 `ProjectRepository.findByStatus(ProjectStatus)`, 기존 `ObjectProvider<ProjectService> selfProvider` 필드.
- Produces: `ProjectService.updateFundedAmount(Long projectId, BigDecimal fundedAmount): void` — 단일 프로젝트 갱신, 낙관적 락 재시도 포함. `ProjectService.reconcileFundedAmounts(): void` — `IN_PROGRESS` 전체를 순회하며 갱신, 한 프로젝트 실패가 나머지를 막지 않음. Task 5에서 스케줄러가 `reconcileFundedAmounts()`를 호출한다.

- [ ] **Step 1: `ProjectServiceImplRetryTest.java`에 실패하는 재시도 테스트 3개를 추가한다**

파일 마지막 테스트(`closeEarly_deactivatesRewards`) 다음, 클래스 닫는 `}` 앞에 추가:

```java

    @Test
    @DisplayName("updateFundedAmount: 락 충돌이 재시도 범위(3회) 안에서 풀리면 정상 반영된다")
    void updateFundedAmount_retriesUntilSuccess() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L))
                .thenReturn(Optional.of(project));

        projectService.updateFundedAmount(1L, BigDecimal.valueOf(300_000));

        assertThat(project.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(300_000));
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("updateFundedAmount: 재시도를 다 소진하면 ConcurrentUpdateFailedException으로 변환된다")
    void updateFundedAmount_exhaustsRetries_throwsConcurrentUpdateFailed() {
        when(projectRepository.findById(anyLong()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertThatThrownBy(() -> projectService.updateFundedAmount(1L, BigDecimal.valueOf(300_000)))
                .isInstanceOf(ConcurrentUpdateFailedException.class);
        verify(projectRepository, times(3)).findById(anyLong());
    }

    @Test
    @DisplayName("updateFundedAmount: 락 충돌이 아닌 검증 예외(음수)는 재시도 없이 원래 타입 그대로 전파된다")
    void updateFundedAmount_negativeAmount_notMasked() {
        when(projectRepository.findById(anyLong())).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.updateFundedAmount(1L, BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모금액은 0 이상");
        verify(projectRepository, times(1)).findById(anyLong());
    }
```

- [ ] **Step 2: 컴파일 실패를 확인한다**

Run: `./gradlew :project-service:compileTestJava`
Expected: FAIL — `cannot find symbol: method updateFundedAmount` (`ProjectService`에 아직 없음)

- [ ] **Step 3: `ProjectService.java`에 인터페이스 메서드 2개를 추가한다**

파일 상단 import 블록 첫 줄(`import com.growmighty.lectures.firstday.common.entity.UserRole;`) 앞에 추가:

```java
import java.math.BigDecimal;

import com.growmighty.lectures.firstday.common.entity.UserRole;
```

`closeProjectByDeadline()` 선언 바로 다음에 추가:

```java
    /** closeExpiredProjects()가 프로젝트 하나씩 재시도 가능하도록 호출하는 단위. 외부에서 직접 부를 일은 없다. */
    void closeProjectByDeadline(Long projectId);

    /**
     * 내부용: order-service에서 pull 조회해온 절대값(누적 총액)으로 한 프로젝트의 fundedAmount를
     * 덮어쓴다. 멱등. FundedAmountReconciliationScheduler가 프로젝트 하나씩 재시도 가능하도록
     * 호출하는 단위 — 외부에서 직접 부를 일은 없다.
     */
    void updateFundedAmount(Long projectId, BigDecimal fundedAmount);

    /** 배치 전용: IN_PROGRESS 프로젝트마다 order-service의 현재 확정 누적 총액을 pull해 보정한다. */
    void reconcileFundedAmounts();
```

- [ ] **Step 4: `ProjectServiceImpl.java`에 구현을 추가한다**

`closeProjectByDeadline()`의 `@Recover` 메서드(`recoverCloseProjectByDeadlineConflict`) 바로 다음에 추가:

```java
    @Recover
    public void recoverCloseProjectByDeadlineConflict(RuntimeException e, Long projectId) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "프로젝트 마감 처리 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
    }

    @Override
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    @Transactional
    public void updateFundedAmount(Long projectId, BigDecimal fundedAmount) {
        getProject(projectId).updateFundedAmount(fundedAmount);
    }

    @Recover
    public void recoverUpdateFundedAmountConflict(RuntimeException e, Long projectId, BigDecimal fundedAmount) {
        if (e instanceof ObjectOptimisticLockingFailureException) {
            throw new ConcurrentUpdateFailedException(
                "모금액 갱신 중 동시 수정 충돌이 반복되어 실패했습니다. projectId=" + projectId);
        }
        throw e;
    }

    /**
     * closeExpiredProjects()와 같은 이유로 한 트랜잭션으로 묶지 않는다 — 프로젝트 하나의 pull 실패나
     * 락 충돌이 같은 배치 실행의 나머지 프로젝트까지 막으면 안 된다. selfProvider로 프록시를 거쳐
     * updateFundedAmount() 한 건마다 독립된 트랜잭션 + 재시도를 갖게 한다.
     */
    @Override
    public void reconcileFundedAmounts() {
        List<Project> inProgress = projectRepository.findByStatus(ProjectStatus.IN_PROGRESS);
        ProjectService self = selfProvider.getObject();
        for (Project project : inProgress) {
            try {
                BigDecimal fundedAmount = orderPort.getFundedAmount(project.getProjectId());
                self.updateFundedAmount(project.getProjectId(), fundedAmount);
            } catch (RuntimeException e) {
                log.warn("모금액 보정 실패. projectId={}", project.getProjectId(), e);
            }
        }
    }
```

파일 상단 import에 `java.math.BigDecimal`을 추가한다 (`java.time.LocalDate` import 바로 위):

```java
import java.time.LocalDate;
```
→
```java
import java.math.BigDecimal;
import java.time.LocalDate;
```

- [ ] **Step 5: 재시도 테스트 통과를 확인한다**

Run: `./gradlew :project-service:test --tests "ProjectServiceImplRetryTest"`
Expected: PASS (전부, 기존 테스트 포함)

- [ ] **Step 6: `reconcileFundedAmounts()`의 부분 실패 격리를 검증하는 새 테스트 파일을 작성한다**

`ProjectServiceImplDeleteTest.java`와 같은 순수 Mockito 패턴(Spring 컨텍스트 없음 — 이 테스트는 재시도 발동이 아니라 루프의 예외 격리 로직만 검증하면 되므로, `selfProvider`가 같은 인스턴스를 돌려주도록 목만 잡아도 충분하다):

```java
package com.growmighty.lectures.firstday.project.project.application;

import com.growmighty.lectures.firstday.common.exception.ServiceUnavailableException;
import com.growmighty.lectures.firstday.project.category.infrastructure.ProjectCategoryRepository;
import com.growmighty.lectures.firstday.project.project.application.port.OrderPort;
import com.growmighty.lectures.firstday.project.project.domain.Project;
import com.growmighty.lectures.firstday.project.project.domain.ProjectStatus;
import com.growmighty.lectures.firstday.project.project.infrastructure.ProjectRepository;
import com.growmighty.lectures.firstday.project.reward.application.RewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * reconcileFundedAmounts()는 @Retryable 대상이 아니라(루프 자체는 재시도 없음, 프로젝트 한 건씩만
 * self.updateFundedAmount()로 재시도) ProjectServiceImplDeleteTest와 같은 이유로 Spring 컨텍스트 없이
 * 순수 Mockito로 검증한다. selfProvider가 이 테스트 인스턴스 자신을 돌려주게 해서 self-invocation을
 * 흉내낸다 — 여기서는 @Retryable 발동 자체(AOP 프록시)가 아니라 루프의 예외 격리만 확인하면 된다.
 * Project.projectId는 @GeneratedValue라 실제 저장 없이는 항상 null이므로, 프로젝트별로 구분되는
 * ID가 필요한 이 테스트에서는 ReflectionTestUtils로 직접 주입한다(ProjectTest의 fundedAmount
 * 주입과 같은 이유).
 */
class ProjectServiceImplReconciliationTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final ProjectCategoryRepository projectCategoryRepository = mock(ProjectCategoryRepository.class);
    private final RewardService rewardService = mock(RewardService.class);
    private final OrderPort orderPort = mock(OrderPort.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<ProjectService> selfProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RewardService> rewardServiceProvider = mock(ObjectProvider.class);

    private ProjectServiceImpl projectService;

    private Project project(Long projectId) {
        Project project = Project.register(1L, null, "title", 1L, "summary", "desc",
                BigDecimal.valueOf(1_000_000), LocalDateTime.now(), LocalDate.now().plusDays(30));
        project.approve();
        ReflectionTestUtils.setField(project, "projectId", projectId);
        return project;
    }

    @BeforeEach
    void setUp() {
        when(rewardServiceProvider.getObject()).thenReturn(rewardService);
        projectService = new ProjectServiceImpl(
                projectRepository, projectCategoryRepository, selfProvider, rewardServiceProvider, orderPort);
        when(selfProvider.getObject()).thenReturn(projectService);
    }

    @Test
    @DisplayName("IN_PROGRESS 프로젝트마다 order-service를 조회해 fundedAmount를 갱신한다")
    void reconcileFundedAmounts_updatesEachInProgressProject() {
        Project p1 = project(1L);
        Project p2 = project(2L);
        when(projectRepository.findByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(List.of(p1, p2));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(orderPort.getFundedAmount(1L)).thenReturn(BigDecimal.valueOf(100_000));
        when(orderPort.getFundedAmount(2L)).thenReturn(BigDecimal.valueOf(200_000));

        projectService.reconcileFundedAmounts();

        assertThat(p1.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        assertThat(p2.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
    }

    @Test
    @DisplayName("한 프로젝트의 order-service 조회가 실패해도 나머지 프로젝트는 계속 갱신된다")
    void reconcileFundedAmounts_onePartialFailure_othersStillUpdated() {
        Project failing = project(1L);
        Project succeeding = project(2L);
        when(projectRepository.findByStatus(ProjectStatus.IN_PROGRESS)).thenReturn(List.of(failing, succeeding));
        when(projectRepository.findById(2L)).thenReturn(Optional.of(succeeding));
        when(orderPort.getFundedAmount(1L)).thenThrow(new ServiceUnavailableException("주문 서비스 응답 없음"));
        when(orderPort.getFundedAmount(2L)).thenReturn(BigDecimal.valueOf(200_000));

        assertThatCode(() -> projectService.reconcileFundedAmounts()).doesNotThrowAnyException();

        assertThat(succeeding.getFundedAmount()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
        assertThat(failing.getFundedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 7: 새 테스트 통과를 확인한다**

Run: `./gradlew :project-service:test --tests "ProjectServiceImplReconciliationTest"`
Expected: PASS (2개)

- [ ] **Step 8: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectService.java \
        project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImpl.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplRetryTest.java \
        project-service/src/test/java/com/growmighty/lectures/firstday/project/project/application/ProjectServiceImplReconciliationTest.java
git commit -m "Feat: ProjectService에 updateFundedAmount/reconcileFundedAmounts 추가"
```

---

### Task 5: `FundedAmountReconciliationScheduler` (1분 주기)

**Files:**
- Create: `project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/FundedAmountReconciliationScheduler.java`

**Interfaces:**
- Consumes: `ProjectService.reconcileFundedAmounts()`(Task 4).

`ProjectDeadlineScheduler`가 `@EnableScheduling`(`ProjectServiceApplication`에 이미 있음, 별도 설정 불필요)에 기대 동작하는 것과 동일한 패턴이라, 별도 단위 테스트 없이(기존 `ProjectDeadlineScheduler`도 전용 테스트가 없음 — `@Scheduled` 위임 한 줄짜리라 과잉 테스트로 판단) 바로 구현하고 컴파일만 확인한다.

- [ ] **Step 1: 스케줄러 파일을 작성한다**

```java
package com.growmighty.lectures.firstday.project.project.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IN_PROGRESS 프로젝트의 fundedAmount를 1분마다 order-service pull 조회로 보정한다.
 * push(주문 확정/취소 시 order-service가 직접 알려주는 방식)는 아직 order-service에 발신 코드가
 * 없어 만들지 않는다 — 이 스케줄러가 push 없이도 fundedAmount를 최신으로 유지하는 유일한 경로다
 * (docs/superpowers/specs/2026-07-28-funded-amount-pull-sync-design.md 참고).
 */
@Component
@RequiredArgsConstructor
public class FundedAmountReconciliationScheduler {

    private final ProjectService projectService;

    @Scheduled(fixedRate = 60 * 1000)
    public void reconcile() {
        projectService.reconcileFundedAmounts();
    }
}
```

- [ ] **Step 2: 컴파일과 전체 project-service 테스트를 확인한다**

Run: `./gradlew :project-service:build`
Expected: BUILD SUCCESSFUL (컴파일 + 전체 테스트 통과. MySQL Testcontainers가 필요하므로 Docker가 떠 있어야 한다 — `docker compose -f infrastructure/docker-compose.yml up -d mysql`)

- [ ] **Step 3: 커밋**

```bash
git add project-service/src/main/java/com/growmighty/lectures/firstday/project/project/application/FundedAmountReconciliationScheduler.java
git commit -m "Feat: 모금액 1분 주기 pull 보정 스케줄러 추가"
```

---

## Self-Review 결과

**스펙 커버리지:**
- "1분 이내 일치" → Task 5 `fixedRate = 60 * 1000`.
- "무후원 프로젝트는 0원 처리" → Task 3 `resolveFundedAmount` + 테스트.
- "order-service 실패해도 다른 프로젝트에 영향 없음" → Task 4 `reconcileFundedAmounts`의 try/catch + 전용 테스트.
- "낙관적 락 충돌 시 재시도/복구" → Task 4 `@Retryable`/`@Recover` + `ProjectServiceImplRetryTest` 3종.
- "push는 이번 범위 제외" → 어떤 태스크에도 `PUT` 수신 엔드포인트나 `FundedAmountUpdateRequest`를 만들지 않음(의도적 누락).

**플레이스홀더 스캔:** "TBD"/"나중에"/"적절히 처리" 패턴 없음. 모든 스텝에 실제 코드 포함.

**타입 일관성:** `OrderPort.getFundedAmount(Long): BigDecimal` (Task 3) → `ProjectServiceImpl.reconcileFundedAmounts()`에서 `orderPort.getFundedAmount(project.getProjectId())`로 그대로 소비(Task 4) — 시그니처 일치 확인. `Project.updateFundedAmount(BigDecimal)`(Task 2) → `ProjectServiceImpl.updateFundedAmount(Long, BigDecimal)`에서 `getProject(projectId).updateFundedAmount(fundedAmount)`로 그대로 소비(Task 4) — 일치 확인.
